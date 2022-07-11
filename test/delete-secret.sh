#!/bin/sh

# delete secret
oc delete secret secret-saas -n ${YAKS_NAMESPACE}
oc delete secret secret-saas-test -n ${YAKS_NAMESPACE}
