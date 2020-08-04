Feature: all integrations print the correct messages

  Background: 
    Given variable username is "citrus:randomString(10)"
    Given URL: INSTANCE_URL
    And HTTP request header Authorization is "Bearer TOKEN"
    And HTTP request header Content-Type is "application/json"
    And HTTP request body:  {  "Name" : "${username}", "Phone" : "123123123", "Website" : "https://github.com/openshift-integration/camel-k-example-saas" }

  Scenario:
    Given integration salesforce-to-workday is running
    Given integration mock-workday is running
    When send POST /services/data/v20.0/sobjects/Account
    Then receive HTTP 201 Created
    And integration salesforce-to-workday should print New Account created: ID: 
    And integration salesforce-to-workday should print Name: ${username}, Phone: 123123123, Website: https://github.com/openshift-integration/camel-k-example-saas
    And integration mock-workday should print Received Workday request:
    And integration mock-workday should print ${username}
