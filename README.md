SimpleJavaMail and JavaMail Performance Tests
=============================================

A naïve performance testing.

Steps to test:

Download [FakeSMTP](http://nilhcem.com/FakeSMTP/)

Run a headless FakeSMTP instance on a remote server.

    java -jar fakeSMTP-2.0.jar -s -b -p 8900 > /dev/null 2>&1
    
Warm-up FakeSMTP by running a bunch of tests first.

To test SimpleJavaMail in parallel mode with 10 (default value) threads run:

    ./gradlew run --args='smt <num-mails-to-send> <smtp-host> <smtp-port>'

To test SimpleJavaMail with connection pool (default size 4) run:

    ./gradlew run --args='sm <num-mails-to-send> <smtp-host> <smtp-port>'
    
To test JavaMail run:

    ./gradlew run --args='jm <num-mails-to-send> <smtp-host> <smtp-port>'

For more information please refer to [simple-java-mail/issues/198](https://github.com/bbottema/simple-java-mail/issues/198).
