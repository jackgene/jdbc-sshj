package io.github.emotionbug.jdbc.sshj;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

@Test
public class SshJDriverTest {
    @Test
    void testVerifyConnection() throws Exception {
        // Set up
        final SshJDriver instance = new SshJDriver();
        final String testJdbcUrl = "jdbc:sshj:dbscheme://dbhost.test.lan:1337/testdb?username=dbuser&ssh.host=sshhost.test.lan&ssh.username=sshuser";

        // Test
        final ConnectionData actualConnData = instance.verifyConnection(testJdbcUrl);

        // Verify
        assertEquals(actualConnData.ourUrl, "jdbc:sshj://sshhost.test.lan:22?remote=dbhost.test.lan:1337&username=sshuser");
        assertEquals(actualConnData.forwardingUrl, "jdbc:dbscheme://{{host}}:{{port}}/testdb?username=dbuser");
    }
}
