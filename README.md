# vtesdecks-back springboot

This project is an Springboot application serving as the back-end for [VTES Decks](vtesdecks.com), a online card search,
TWD browser and deck builder for Vampire the Eternal Struggle (VTES)

## Prerequisites

Before you begin, ensure you have met the following requirements:

- Java 17
- Maven 3.6.0 or higher
- MySQL 8.0.21
- A tool for API testing (e.g., Postman or cURL)

## Getting Started

Follow these steps to set up the project on your local machine.

### 1. Clone the Repository

```bash
Copiar código
git clone <repository-url>
cd <project-directory>
```

### 2. Set Up MySQL Database

- Start your MySQL server.
- Create a database for the project:

```sql 
CREATE DATABASE vtes_decks_v1;
````

- Update the src/main/resources/application-local.properties file with your MySQL database configuration:

```properties
datasource.jdbcUrl=jdbc:mysql://localhost:3306/<database_name>
datasource.username=<your_mysql_username>
datasource.password=<your_mysql_password>
```

### 3. Build the Application

Build the application using Maven:

```bash
mvn clean install
```

### 4. Run the Application with Local Profile

Run the Spring Boot application using the local profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Alternatively, you can run the application as a JAR file:

```bash
java -jar target/spring-boot-vtes-decks-<version>.jar --spring.profiles.active=local
```

### 5. Run the Scheduler Manually

After starting the application, manually trigger the scheduler to scrape tournament winning decks by sending a request
to the following endpoint:

- Endpoint: /admin/scheduler/scrap_decks
- Method: POST

Use an API testing tool or cURL to trigger the scheduler:

```bash
curl -X POST http://localhost:8080/admin/scheduler/scrap_decks
```

## Additional Information

The application will listen on port 8080 by default. You can change this by updating the server.port property in the
application-local.properties file.

Make sure the MySQL service is running, and the credentials provided in the application-local.properties are correct to
avoid connection issues.

## Troubleshooting

If you encounter any issues with the database connection, ensure that your MySQL server is running and that the
credentials and database URL are correct.
Check the application logs for any errors during startup or when triggering the scheduler.

## CONTRIBUTION

Contributions are welcome! Please create an issue to discuss your proposed feature or fix before starting work on it.

We don't have specific coding style or test requirements at this time.
