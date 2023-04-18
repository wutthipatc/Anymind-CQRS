# Anymind-CQRS
## An Akka Scala project provides the concept of CQRS using Actor persistence
### Preparation
* Install Docker runtime and Docker-compose on your machine
* Go to project directory
* Run `docker-compose -f kafka-docker-compose.yml` up to start Kafka and Zookeeper Bootstrap server at localhost:9092
* Run `docker-compose -f mysql-docker-compose.yml` up to start MySQL database at localhost:3306
* Install MySQL workbench or other MySQL client to access the database then create all necessary table from `mysql-schema.sql` file on the database name `db`
* Download `kafka-3.4.0-src` attached on `https://app.greenhouse.io/tests/abd90e0e4153c51e9055690d8323096e?utm_medium=email&utm_source=TakeHomeTest` to run the consumer to be able to see the message consume from the specific topic `bitcoin-topic`
* Go to `kafka-3.4.0-src` and run command `bin/kafka-console-consumer.sh --topic bitcoin-topic  --from-beginning --bootstrap-server localhost:9092` to start consuming the message published from the application
* Install Postman to be able to import Postman collection to call the APIs provided by application
* Import Postman collection `BitCoin wallet.postman_collection.json`

### Running the application
* Clone this repository to your local machine
* Open the application using IntelliJ Idea 
* **Run the `sbt` command from terminal inside the project to start `sbt shell` or you can use `sbt shell view` on Menu bar from IntelliJ and run command** `run` or **click on play button Main object to start the application** 
* Press ctrl+c to stop sbt shell or press stop button from the IDE

### Enjoy calling APIs via Postman and see the result
* if there is an issue you can see the log output and response error message that should be related to request validtion error 
* The add bit coin API must be called subsequently with `dateTime` field greater than or equal the last add call
* Please be aware that the request could be sent the `dateTime` field with variety of time zone and MySQL only store the UTC time
* Playing around with calling and see the Kafka consumer client and MySQL records
* Try to stop the application by ctrl+c from sbt shell or from stop button on IntelliJ and restart to see if it continuously playing from the last state

### Note
* The Kafka consumer part just consumes the message to *simulate the read side of CQRS* but the actual Persistence actor is only on the write side which we use there record on both write and read sides (Actually we can just create the same Persistence actor receiving command from Kafka consumer event transformed)
* **The wallet is initiated from the time you first run the application with 1000 BTC. This means you need to add the amount specifying the time after the time of first run** and always adding the amount after the `dateTime` of latest one
