#!/bin/sh

# create secret from properties file
oc create secret generic secret-saas --from-file=../secret-saas.properties -n ${YAKS_NAMESPACE}
oc create secret generic secret-saas-test --from-file=secret-saas-test.properties -n ${YAKS_NAMESPACE}
# bind secret to test by name
oc label secret secret-saas-test yaks.citrusframework.org/test=saas -n ${YAKS_NAMESPACE}

