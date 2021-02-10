Feature: integration prints expected messages

  Background:
    Given variable subject is "Case regarding citrus:randomString(10)"
    Given variable description is "Test case for Salesforce to ServiceNow integration"
    Given variable token_request is "grant_type=password&client_id=${camel.component.salesforce.clientId}&client_secret=${camel.component.salesforce.clientSecret}&username=${camel.component.salesforce.username}&password=${camel.component.salesforce.password}"
    And URL: ${camel.component.salesforce.loginUrl}
    And HTTP request header Content-Type="application/x-www-form-urlencoded"
    And HTTP request body: ${token_request}
    When send POST /services/oauth2/token
    Then verify HTTP response expression: $.instance_url="@variable(instance_url)@"
    And verify HTTP response expression: $.access_token="@variable(access_token)@"
    And receive HTTP 200 OK

    Given URL: ${instance_url}
    And HTTP request header Authorization="Bearer ${access_token}"
    And HTTP request header Content-Type="application/json"
    And HTTP request query parameter q="citrus:urlEncode('SELECT Id,Name FROM Account LIMIT 1')"
    When send GET /services/data/v20.0/query/
    Then verify HTTP response expression: $.records[0].Id="@variable(account_id)@"
    And receive HTTP 200 OK

    Given HTTP request header Authorization="Bearer ${access_token}"
    And HTTP request header Content-Type="application/json"
    And HTTP request body: {  "Subject" : "${subject}", "AccountId": "${account_id}", "Description" : "${description}" }

  Scenario: Integration consumes newly created Salesforce object
    Given Camel-K integration salesforce-to-service-now is running
    When send POST /services/data/v20.0/sobjects/Case
    Then receive HTTP 201 Created
    And Camel-K integration salesforce-to-service-now should print New Case: ID:
    And Camel-K integration salesforce-to-service-now should print Subject: ${subject}
    And Camel-K integration salesforce-to-service-now should print Created Incident with number
    And Camel-K integration salesforce-to-service-now should print Updated Case with Incident number
