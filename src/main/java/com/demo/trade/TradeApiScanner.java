package com.demo.trade;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sns.model.SetSMSAttributesRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class TradeApiScanner implements RequestHandler<Object, String> {

    @Override
    public String handleRequest(Object input, Context context) {
        LambdaLogger logger = context.getLogger();
        String companyName = input.toString();
        logger.log("Input: " + companyName + "\n");

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpGet getRequest = new HttpGet(
                    "https://apidojo-yahoo-finance-v1.p.rapidapi.com/stock/v2/get-summary?region=US&symbol=" + companyName);
            getRequest.addHeader("x-rapidapi-host", System.getenv("x_rapidapi_host"));
            getRequest.addHeader("x-rapidapi-key", System.getenv("x_rapidapi_key"));
            CloseableHttpResponse response = httpClient.execute(getRequest);
            String responseString = EntityUtils.toString(response.getEntity(), "utf-8");
            ObjectMapper objectMapper = new ObjectMapper();

            Map<String, Object> responseMap = objectMapper.readValue(responseString, new TypeReference<Map<String, Object>>() {
            });
            BigDecimal currentPrice = new BigDecimal((String) ((Map<String, Object>) ((Map<String, Object>) responseMap.get("price"))
                    .get("regularMarketPrice")).get("fmt"));
            BigDecimal startPrice = new BigDecimal((String) ((Map<String, Object>) ((Map<String, Object>) responseMap.get("price"))
                    .get("regularMarketOpen")).get("fmt"));
            logger.log("currentPrice: " + currentPrice.toString() + "\n");
            logger.log("startPrice: " + startPrice.toString() + "\n");

            String phoneNumber = System.getenv("PHONE_NUMBER");
            int compare = currentPrice.compareTo(startPrice);
            AmazonSNSClient snsClient = new AmazonSNSClient();
            if (compare > 0) {
                String sellStocksMan = "Sell " + companyName + " stocks man";
                sendSMSMessage(snsClient, sellStocksMan, phoneNumber, logger);
                return sellStocksMan;
            } else if (compare < 0) {
                String buyStocksMan = "Buy " + companyName + " stocks man";
                sendSMSMessage(snsClient, buyStocksMan, phoneNumber, logger);
                return buyStocksMan;
            }
        } catch (IOException e) {
            logger.log("Error during the call " + e.getMessage() + "\n");
            throw new RuntimeException("Error during the api call", e);
        }
        return "Be patient";
    }

    public static void sendSMSMessage(AmazonSNSClient snsClient, String message,
                                      String phoneNumber, LambdaLogger logger) {
        logger.log(message + "\n");
        Map<String, MessageAttributeValue> smsAttributes =
                new HashMap<>();
        smsAttributes.put("AWS.SNS.SMS.SenderID", new MessageAttributeValue()
                .withStringValue("mySenderID") //The sender ID shown on the device.
                .withDataType("String"));
        smsAttributes.put("AWS.SNS.SMS.SMSType", new MessageAttributeValue()
                .withStringValue("Promotional") //Sets the type to promotional.
                .withDataType("String"));
        SetSMSAttributesRequest setRequest = new SetSMSAttributesRequest()
                .addAttributesEntry("MonthlySpendLimit", "15")
                .addAttributesEntry("DeliveryStatusSuccessSamplingRate", "10")
                .addAttributesEntry("DefaultSMSType", "Promotional");
        snsClient.setSMSAttributes(setRequest);
        PublishResult result = snsClient.publish(new PublishRequest()
                .withMessage(message)
                .withPhoneNumber(phoneNumber)
                .withMessageAttributes(new HashMap<>())
        );
        logger.log("Sent message ID:" + result.getMessageId() + "\n");
    }
}
