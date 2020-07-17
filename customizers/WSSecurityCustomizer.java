package customizers;

import java.util.Map;
import java.util.HashMap;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;

import org.apache.camel.BindToRegistry;
import org.apache.camel.PropertyInject;
import org.apache.camel.component.cxf.CxfConfigurer;
import org.apache.camel.component.cxf.ChainedCxfConfigurer;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.ext.WSPasswordCallback;

public class WSSecurityCustomizer {

    @PropertyInject("workday-username")
    private String workdayUsername;
    
    @PropertyInject("workday-password")
    private String workdayPassword;
    
    @BindToRegistry("wssecClientConfigurer")
    public CxfConfigurer getClientCxfConfigurer() {
        return new ChainedCxfConfigurer.NullCxfConfigurer() {

            @Override
            public void configureClient(Client client) {
                client.getEndpoint().getOutInterceptors().add(new WSS4JOutInterceptor(getWSSEProperties()));
            }
        };
    }

    @BindToRegistry("wssecServerConfigurer")
    public CxfConfigurer getServerCxfConfigurer() {
        return new ChainedCxfConfigurer.NullCxfConfigurer() {

            @Override
            public void configureServer(Server server) {
                server.getEndpoint().getInInterceptors().add(new WSS4JInInterceptor(getWSSEProperties()));
            }
        };
    }

    private Map<String, Object> getWSSEProperties() {
        final Map<String, Object> outProps = new HashMap<>();
        final StringBuilder actions = new StringBuilder();

        outProps.put(ConfigurationConstants.USER, workdayUsername);
        outProps.put(ConfigurationConstants.PW_CALLBACK_REF, (CallbackHandler) callbacks -> {
            for (Callback callback : callbacks) {
                final WSPasswordCallback passwordCallback = (WSPasswordCallback) callback;
                if (workdayUsername.equals(passwordCallback.getIdentifier())) {
                    passwordCallback.setPassword(workdayPassword);
                    return;
                }
            }
        });

        outProps.put(ConfigurationConstants.PASSWORD_TYPE, "PasswordText");
        actions.append(ConfigurationConstants.USERNAME_TOKEN);
        actions.append(' ');
        actions.append(ConfigurationConstants.TIMESTAMP);

        outProps.put(ConfigurationConstants.ADD_USERNAMETOKEN_NONCE, "true");
        outProps.put(ConfigurationConstants.ADD_USERNAMETOKEN_CREATED, "true");

        outProps.put(ConfigurationConstants.ACTION, actions.toString());

        return outProps;
    }
}