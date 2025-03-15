## Running the prototype

### Pre-requisites
1. Java
2. Maven
3. OAuth 2.0 client (Refer Google APIs for more details)
4. Redis (I used Docker to run Redis locally. You can use any other method)

### Build & Test
```
mvn clean install
```

### Run as Spring Boot application
```
mvn spring-boot:run
```

### Environment variables
- Create a `.env` file in the root folder of the project and add the following environment variables
  - `REDIS_PUBLIC_URL` - Redis URL (ex: `localhost:6379`)
  - `REDIS_PASSWORD` - Redis password