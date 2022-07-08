Feature: Salesforce to ServiceNow

  Background:
    Given variable apiVersion is "50.0"
    Given variable token_request is "grant_type=password&client_id=${salesforce-clientId}&client_secret=${salesforce-clientSecret}&username=${salesforce-username}&password=${salesforce-password}"
    Given URL: ${salesforce-loginUrl}
    And HTTP request header Content-Type="application/x-www-form-urlencoded"
    And HTTP request body: ${token_request}
    # Retrieve Salesforce instance url and access token
    When send POST /services/oauth2/token
    Then verify HTTP response expression: $.instance_url="@variable(instance_url)@"
    And verify HTTP response expression: $.access_token="@variable(access_token)@"
    And receive HTTP 200 OK

  Scenario: Trigger SalesForce API and verify ServiceNow result
    # Retrieve Salesforce account id
    Given URL: ${instance_url}
    And HTTP request header Authorization="Bearer ${access_token}"
    And HTTP request header Content-Type="application/json"
    And HTTP request query parameter q="citrus:urlEncode('SELECT Id,Name FROM Account LIMIT 1')"
    When send GET /services/data/v${apiVersion}/query/
    Then verify HTTP response expression: $.records[0].Id="@variable(account_id)@"
    And receive HTTP 200 OK

    # Load Camel K integration and make sure it is running
    Given Camel K integration salesforce-to-service-now should be running
    And Camel K integration salesforce-to-service-now should print Subscribed to channel /topic/CamelCaseTopic

    # Create Salesforce case object
    Given variable subject is "Case regarding citrus:randomString(10)"
    Given variable description is "Test case for Salesforce to ServiceNow integration"
    Given HTTP request header Authorization="Bearer ${access_token}"
    And HTTP request header Content-Type="application/json"
    And HTTP request body: {  "Subject" : "${subject}", "AccountId": "${account_id}", "Description" : "${description}" }
    When send POST /services/data/v${apiVersion}/sobjects/Case
    Then receive HTTP 201 Created

    # Verify Camel K integration consumed newly created case
    And Camel K integration salesforce-to-service-now should print New Case: ID:
    And Camel K integration salesforce-to-service-now should print Subject: ${subject}
    And Camel K integration salesforce-to-service-now should print Created Incident with number
    And Camel K integration salesforce-to-service-now should print Updated Case with Incident number

  Scenario: Remove Camel-K resources
    Given delete Camel K integration salesforce-to-service-now
