package nu.studer.gradle.credentials;

import nu.studer.gradle.credentials.domain.CredentialsEncryptor;
import nu.studer.gradle.credentials.domain.CredentialsPersistenceManager;
import nu.studer.java.util.OrderedProperties;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;

/**
 * Adds/updates the given credentials, specified as project properties.
 */
public class AddCredentialsTask extends DefaultTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddCredentialsTask.class);

    private CredentialsEncryptor credentialsEncryptor;
    private CredentialsPersistenceManager credentialsPersistenceManager;
    private String env;
    private String hashedKeys = "true";
    private String loc;
    private String pass;
    private String key;
    private String value;

    public void setCredentialsEncryptor(CredentialsEncryptor credentialsEncryptor) {
        this.credentialsEncryptor = credentialsEncryptor;
    }

    public void setCredentialsPersistenceManager(CredentialsPersistenceManager credentialsPersistenceManager) {
        this.credentialsPersistenceManager = credentialsPersistenceManager;
    }

    @Option(option = "env", description = "The credentials env.")
    public void setEnv(String env) {
        this.env = env;
    }

    @Option(option = "loc", description = "The credentials location.")
    public void setLoc(String loc) {
        this.loc = loc;
    }

    @Option(option = "pass", description = "The credentials pass.")
    public void setPass(String pass) {
        this.pass = pass;
    }

    @Option(option = "key", description = "The credentials key.")
    public void setKey(String key) {
        this.key = key;
    }

    @Option(option = "hashed", description = "The credentials hashedKeys state.")
    public void setHashed(String hashedKeys) {
        this.hashedKeys = hashedKeys;
    }

    @Option(option = "value", description = "The credentials value.")
    public void setValue(String value) {
        this.value = value;
    }

    @Internal("Do not annotate as @Input to avoid the env being stored in the task artifact cache")
    public String getCredentialsEnv() {
        return env != null ? env : getProjectProperty(CredentialsPlugin.CREDENTIALS_ENV_PROPERTY);
    }

    @Internal("Do not annotate as @Input to avoid the loc being stored in the task artifact cache")
    public String getCredentialsLoc() {
        return loc != null ? loc : getProjectProperty(CredentialsPlugin.CREDENTIALS_LOCATION_PROPERTY);
    }

    @Internal("Do not annotate as @Input to avoid the pass being stored in the task artifact cache")
    public String getCredentialsPass() {
        return pass != null ? pass : getProjectProperty(CredentialsPlugin.CREDENTIALS_PASSPHRASE_PROPERTY) != null ? getProjectProperty(CredentialsPlugin.CREDENTIALS_PASSPHRASE_PROPERTY) : CredentialsPlugin.DEFAULT_PASSPHRASE;
    }

    @Internal("Do not annotate as @Input to avoid the hashedKeys being stored in the task artifact cache")
    public Boolean getCredentialsHashedKeys() {
        return hashedKeys != null && hashedKeys.toLowerCase().matches("yes|true|on|1|enabled");
    }

    @Internal("Do not annotate as @Input to avoid the key being stored in the task artifact cache")
    public String getCredentialsKey() {
        return key != null ? key : getProjectProperty(CredentialsPlugin.CREDENTIALS_KEY_PROPERTY);
    }

    @Internal("Do not annotate as @Input to avoid the value being stored in the task artifact cache")
    public String getCredentialsValue() {
        return value != null ? value : getProjectProperty(CredentialsPlugin.CREDENTIALS_VALUE_PROPERTY);
    }

    @OutputFile
    public File getEncryptedPropertiesFile() {
        return credentialsPersistenceManager.getCredentialsFile();
    }

    @TaskAction
    void addCredentials() {
        String env = getCredentialsEnv();
        String loc = getCredentialsLoc();
        boolean hashedKeys = getCredentialsHashedKeys();
        String pass = getCredentialsPass();
        // get credentials key and value from the command line or project properties
        String key = getCredentialsKey();
        if (key == null) {
            throw new IllegalArgumentException("Credentials key must not be null");
        }

        String value = getCredentialsValue();
        if (value == null) {
            throw new IllegalArgumentException("Credentials value must not be null");
        }

        if (env != null || pass != null || loc != null) {
            credentialsPersistenceManager = CredentialsPersistenceManager.fromCredentialsPersistenceManager(CredentialsPlugin.deriveFileNameFromPassphraseAndEnv(env, pass), loc, credentialsPersistenceManager);
            if (pass != null) {
                credentialsEncryptor = CredentialsEncryptor.withPassphrase(pass.toCharArray());
            }
        }
        char[] placeholderValue = new char[value.length()];
        Arrays.fill(placeholderValue, '*');

        // read the current persisted credentials
        OrderedProperties credentials = credentialsPersistenceManager.readCredentials();

        // encrypt value and update credentials
        String hashedKey = credentialsEncryptor.hash(key);

        if(!hashedKeys && credentials.containsProperty(hashedKey)) {
            LOGGER.warn("Collision: {} and {}. \nRemoved: {}={}", key, hashedKey, hashedKey, credentials.getProperty(hashedKey));
            credentials.removeProperty(hashedKey);
        } else if(hashedKeys && credentials.containsProperty(key)) {
            LOGGER.warn("Collision: {} and {}. \nRemoved: {}={}", key, hashedKey, key, credentials.getProperty(key));
            credentials.removeProperty(key);
        }

        String encryptedValue = credentialsEncryptor.encrypt(value);
        credentials.setProperty(hashedKeys ? hashedKey : key, encryptedValue);

        LOGGER.info("AddCredentials: env: '{}' key: '{}->{}' value: '{}'", env, key, hashedKey,  new String(placeholderValue));

        // persist the updated credentials
        credentialsPersistenceManager.storeCredentials(credentials);
    }

    private String getProjectProperty(String key) {
        return (String) getProject().getProperties().get(key);
    }

}
