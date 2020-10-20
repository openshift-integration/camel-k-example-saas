Feature: integration prints expected messages

  Background: 
    Given variable subject is "Case regarding citrus:randomString(10)"
    Given variable description is "Test case for Salesforce to ServiceNow integration"
    Given variable accountId is "ACCOUNT_ID"
    Given URL: INSTANCE_URL
    And HTTP request header Authorization is "Bearer TOKEN"
    And HTTP request header Content-Type is "application/json"
    And HTTP request body:  {  "Subject" : "${subject}", "AccountId": "${accountId}", "Description" : "${description}" }

  Scenario:
    Given integration salesforce-to-service-now is running
    When send POST /services/data/v20.0/sobjects/Case
    Then receive HTTP 201 Created
    And integration salesforce-to-service-now should print New Case: ID: 
    And integration salesforce-to-service-now should print Subject: ${subject}
    And integration salesforce-to-service-now should print Created Incident with number 
    And integration salesforce-to-service-now should print Updated Case with Incident number 
