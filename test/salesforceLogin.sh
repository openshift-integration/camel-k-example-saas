function readProperty() {
    cat "${SOURCE_DIR}/../secret-saas.properties" | grep $1 | cut -d'=' -f2
}

SOURCE_DIR=$( dirname "${BASH_SOURCE[0]}")
TEST_FILE="${SOURCE_DIR}/integration.feature"

cp "${TEST_FILE}" "${TEST_FILE}.backup"

# get login URL and TOKEN
RESPONSE=$(curl -s $(readProperty "camel.component.salesforce.loginUrl")/services/oauth2/token \
      -d "grant_type=password" -d "client_id=$(readProperty 'camel.component.salesforce.clientId')" \
      -d "client_secret=$(readProperty 'camel.component.salesforce.clientSecret')" \
      -d "username=$(readProperty 'camel.component.salesforce.username')" -d "password=$(readProperty 'camel.component.salesforce.password')")

URL=$(echo ${RESPONSE} | jq -r '.instance_url')
TOKEN=$(echo ${RESPONSE} | jq -r '.access_token')

sed -i "s#INSTANCE_URL#${URL}#g" "${TEST_FILE}"
sed -i "s/TOKEN/${TOKEN}/g" "${TEST_FILE}"

# get test ACCOUNT_ID
RESPONSE=$(curl -s ${URL}/services/data/v20.0/query/?q=SELECT%20Id,Name%20FROM%20Account%20LIMIT%201 \
      -H "Authorization: Bearer ${TOKEN}" \
      -H 'Content-Type: application/json')

ACCOUNT_ID=$(echo ${RESPONSE} | jq -r '.records[0].Id')

sed -i "s/ACCOUNT_ID/${ACCOUNT_ID}/g" "${TEST_FILE}"
