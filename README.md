# jdbc-sshj

A JDBC Driver Wrapper which connects over SSH to the target database. Please note that only one port is fowarded,
so forget about connecting to Oracle on Windows.

This project was created using code from [jdbc-ssh](https://github.com/monkeysintown/jdbc-ssh) but the underlying
implementation of SSH has been switched from [JSch](http://www.jcraft.com/jsch/) to 
[SSHJ](https://github.com/hierynomus/sshj) which seems to be updated more frequently, and knows how to connect to 
newer encryption standards of SSH.

The Driver will by default load your `known_hosts` file and will refuse to connect if the host is not in the list.
To supress this behaviour, set `verify_hosts=off`.
 
## Usage
 
Usage is simple:
- drop the JAR into your project (or include it as maven dependency)
- prepend "jdbc:sshj:" command string before your URL
- put the placeholder `{{port}}` in your old JDBC url.

### Syntax

The JDBC-SSHJ uses the following syntax:
```
jdbc:sshj://<host>[:<port>]
   ?remote=<host:port>
	[&username=<ssh username>]
	[&password=<ssh password>]
	[&private.key.file=<location of your private key file>]
	[&private.key.password=<password for the private key>]
	[&private.key.file.format=(PUTTY|OPENSSH)]
	[&verify_hosts=(on|off)]
	;;;
	<your-original-url-with-{{port}}>
```

| Parameter | Description | Example |                                                                       
| --- | --- | --- |                         
| *jdbc:sshj://<host>[:<port>]* | The *host* and *port* of the remote SSH server. Port is optional. | `jdbc:sshj://demo.example.org` | 
| *remote* | The *host* and *port* of the database on the remote server. | `10.11.12.13:5432` |
| *username* | The SSH username. | `demo` |
| *password*| The SSH password, if you want to try password authentication. | `demo123` |
| *private.key.file* | Path to the file with a private key. | `~/.ssh/id_rsa` |
| *private.key.password* | Password for the private key, if any. | `demo1234` |
| *private.key.file.format* | File format. Putty private key files and OpenSSH files are accepted. By default it tries to load OPENSSH format. | `PUTTY` | 
| *verify_hosts* | Supress host verification. Driver will not complain on new/unknown hosts. | `off` | 

Please note that the driver will open a local port and forward it to the server. It will inject the local host and port into your original 
JDBC URL when it sees the text `{{host}}` and `{{port}}`, respectively.

Driver will listen on a local IP in the range from 127.0.1.2 - 127.0.1.200 and a random port in the range of 20000 - 20110. On OS X, though,
only 127.0.0.1 is used, as Mac by default doesn't listen to anything else than this IP. See 
[StackOverflow](https://superuser.com/questions/458875/how-do-you-get-loopback-addresses-other-than-127-0-0-1-to-work-on-os-x) for more
details on this.
	

### Example

If your original URL was:
```
jdbc:postgresql://10.10.11.11:5432/demo-1?ssl=true&allowEncodingChanges=true
```

And you need to connect to this server from the outside, change it like this (split into lines for legibility):
```
jdbc:sshj://demo.example.org
	?username=admin
	&private.key.file=~/.ssh/id_rsa
	&private.key.password=admin12345
	&remote=10.10.11.11:5432
	;;;
	jdbc:postgresql://{{host}}:{{port}}/demo-1?ssl=true&allowEncodingChanges=true
```





## Build

If you just want to compile the project without running the tests:

```
mvn -DskipTests clean install
```

If you want to run the tests (Derby and H2 in server mode):

```
mvn -Djdbc.ssh.username="xxx" -Djdbc.ssh.password="xxx" clean install
```

NOTE: 

If your SSH server is not running on the default port 22 and/or localhost then you can change those parameters:

```
mvn -Djdbc.ssh.username="xxx" -Djdbc.ssh.password="xxx" -Djdbc.ssh.host="192.168.0.1" -Djdbc.ssh.port="2222" clean install
```

At the moment a locally running SSH server is needed for the tests. The embedded SSH server in the unit tests is not yet 
ready (authentication works, but port forwarding fails at the moment).

## Maven dependencies

You can find the latest releases here:

[ ![Download](https://api.bintray.com/packages/cheetah/monkeysintown/jdbc-ssh/images/download.svg) ](https://bintray.com/cheetah/monkeysintown/jdbc-ssh/_latestVersion)

... or setup your Maven dependencies:

```xml
<dependency>
    <groupId>com.github.monkeysintown</groupId>
    <artifactId>jdbc-ssh</artifactId>
    <version>1.0.9</version>
</dependency>
```

... and configure Bintray's JCenter repository in your pom.xml:
 
```xml
...
<repositories>
    <repository>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
        <id>central</id>
        <name>bintray</name>
        <url>http://jcenter.bintray.com</url>
    </repository>
</repositories>
...
```

Get automatic notifications about new releases here:

[ ![Get automatic notifications about new "jdbc-ssh" versions](https://www.bintray.com/docs/images/bintray_badge_color.png) ](https://bintray.com/cheetah/monkeysintown/jdbc-ssh/view?source=watch)
