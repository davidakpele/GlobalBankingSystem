# Wallet / Deposit / Withdraw Service 

> This Service is responsible of creating e-wallet for user where User can make deposit in their currency and withdral or transfer.

## Table of Contents
* **Introduction**
* **Quick Start**
* **Folder Structure**
* **Application Configuration**
* **JWT Configuration**
* **Security Configuration**
* **RabbitMQ Configuration**
* **RabbitMQ Message Producer**
* **Error Handling**
* **File Handling as Receipt Generated**
* **Data Transfer Objects**
* **Rest API Controller**
* **Repositories**
* **Services**
* **Responses**
* **Mockito Testing**

## Introduction
> Authentication Service provides JSON Web Tokens to users during login process that enable them to operate on their wallet.

## Quick Start
> This service forcus on designing wallet balance, deposit action, withdraw action and history
#### How This Work?
- First time user login into, User will be ask to created transfer pin, this pin consist of 4 digit number of their choice.
- Pin will be use to process transfer or any withdraws for security reasons and user can change it anytime.
- User has `Level 2 Option` to set up 2FA-Authentication, means every transfer or any withdraws `OPT` pin will be send to User email instead of having default PIN.
- To Deposit user has options to choose the payment service they want to use "Paystack or flutterWave" and when deposit request is send application check which platform user select to make their deposit so that the application can user request to the platform requesting credit and debit action should taken. ```Deposit from user bank and credit user wallet in the platform``` .
- Deposit success check and send notification to user about their current wallet balance and action taken with the help of RabbitMQ.
- User can transfer money to another user in same platform using recipient username example ***```{'recipientUsername':'John'}```*** and if transaction is successful application automatically send notification message to both users ***```Debit message to sender and Credit message to the recipient```***.
- User can view their Transaction history, filter by date, days and year.
- User can always print transaction receipt because every transaction generate receipt and save it into the database, User can either download immidiately after transaction is successful or comes back to download it from transaction history. This is keep transparency in the platform service which they provide to customers and helps users to keep track of their transactions.
- Design the system with secure data storage, encryption, and audit trails. Regularly update policies to align with evolving regulations.
- Implement centralized logging (e.g., ELK stack) and distributed tracing (e.g., Zipkin, Jaeger) to diagnose and resolve issues promptly.
- Built middlware Detecting Sophisticated Fraud Patterns with asynchronous processing.
- Use machine learning models to enhance behavioral analysis and detect sophisticated patterns.
- Implement regular audits and updates to ensure compliance with the latest regulations.
- Employ real-time monitoring tools to provide instant alerts and actionable insights for flagged activities.
- Created Mockito Unit testing / Junit testing for every phase of the application.

## Project Structure 

```
C:.
├───.mvn
│   └───wrapper
├───src
│   ├───main
│   │   ├───java
│   │   │   └───com
│   │   │       └───example      
│   │   │           └───deposit  
│   │   │               ├───config     
│   │   │               ├───controllers
│   │   │               ├───dto        
│   │   │               ├───enums      
│   │   │               ├───exceptions
│   │   │               ├───messageProducer
│   │   │               │   └───requests
│   │   │               ├───middleware
│   │   │               ├───models
│   │   │               ├───payloads
│   │   │               ├───properties
│   │   │               ├───repository
│   │   │               ├───responses
│   │   │               ├───serviceImplementations
│   │   │               ├───services
│   │   │               └───utils
│   │   └───resources
│   │       ├───static
│   │       │   └───image
│   │       └───templates
│   └───test
│       └───java
│           └───com
│               └───example
│                   └───deposit
└───target
    ├───classes
    │   ├───com
    │   │   └───example
    │   │       └───deposit
    │   │           ├───config
    │   │           ├───controllers
    │   │           ├───dto
    │   │           ├───enums
    │   │           ├───exceptions
    │   │           ├───messageProducer
    │   │           │   └───requests
    │   │           ├───middleware
    │   │           ├───models
    │   │           ├───payloads
    │   │           ├───properties
    │   │           ├───repository
    │   │           ├───responses
    │   │           ├───serviceImplementations
    │   │           ├───services
    │   │           └───utils
    │   └───templates
    ├───generated-sources
    │   └───annotations
    ├───generated-test-sources
    │   └───test-annotations
    ├───maven-status
    │   └───maven-compiler-plugin
    │       ├───compile
    │       │   └───default-compile
    │       └───testCompile
    │           └───default-testCompile
    └───test-classes
        └───com
            └───example
                └───deposit
```
## Application Configuration 
> This ApplicationConfiguration class is slightly different from authentication service `Application Configuration class` because we only create `User Model class` in `application service` so we will capitalize on that and use `WebClient`.

  - Why creating another ApplicationConfiguration or why even trying to locked this service again when we already have authentication service?
      - Well it easy to think that as a beginner. Even though we have authentication service that generate our json web token, that doesn't guaranty this deposit service is secure because it isn't and it vulnerable if we fail to secure it so to secure it we need to lock the entire application down and use the json web token generated from authentication service login to access this service.
    > **NOTE This service by defualt microservices architecture we need to be divided into _FOUR SERVICES_  as follow:**
      - Wallet Service
      - Deposit Service
      - Withdraw Service
      - History Service 
    > But this is a prototype and other developers might find something useful to learn from it.
    
    _IF YOU ARE TO CREATE REAL LIFE PROJECT LIKE THIS WITH MICRO-SERVICE ARCHITECTURE, I WOULD ADVICE YOU TO SEPARATE THIS PARTICULAR SERVICE INTO SMALL PIECES LIKE WHAT I JUST SHOW ABOVE_


```
@Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            try {
                String token = tokenExtractor.extractToken(request);
                UserDTO userDTO = userServiceClient.getUserByUsername(username, token);

                if (userDTO != null) {
                    // Map user roles if necessary, here defaulting to ROLE_USER
                    List<SimpleGrantedAuthority> authorities = userDTO.getRecords().stream()
                            .map(record -> new SimpleGrantedAuthority("ROLE_USER"))
                            .toList();

                    return new org.springframework.security.core.userdetails.User(
                            userDTO.getUsername(),
                            "", 
                            userDTO.isEnabled(),
                            true, // Account non-expired
                            true, // Credentials non-expired
                            !isAccountLocked(userDTO), // Account non-locked
                            authorities
                    );
                } else {
                    throw new UsernameNotFoundException("User not found: " + username);
                }
            } catch (Exception e) {
                // Log the error and throw an exception to prevent unauthorized access
                System.err.println("Error retrieving user details: " + e.getMessage());
                throw new UsernameNotFoundException("Unable to fetch user details for: " + username, e);
            }
        };
    }

    // Helper method to determine if the account is locked
    private boolean isAccountLocked(UserDTO userDTO) {
        return userDTO.getRecords().stream().anyMatch(UserRecordDTO::isLocked);
    }
```
- This code above shows us instead of using userRepository to fetch user directly from the database we are using `UserServiceClient` and below you will see `UserServiceClient` is using WebClient class.

> This class, UserServiceClient, is a Spring service responsible for making HTTP requests to an external user-related API using WebClient, a reactive, non-blocking HTTP client.
```
@Service
public class UserServiceClient {

    private final WebClient webClient;

    @Autowired
    public UserServiceClient(WebClient.Builder webClientBuilder, @Value("${auth-service.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public UserDTO getUserByUsername(String username, String token) {
        return this.webClient.get()
                .uri("/api/v1/user/by/username/{username}", username)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorMessage -> {
                                    if (clientResponse.statusCode().is4xxClientError()) {
                                        String details = extractDetailsFromError(errorMessage);
                                        return Mono.error(new UserNotFoundException("User not found", details));
                                    }
                                    return Mono.error(new RuntimeException("Server error"));
                                }))
                .bodyToMono(UserDTO.class)
                .block();
    }
}
```
### Dependencies Injected
- `WebClient.Builder:` Configures the `WebClient` with a base URL and other settings.
- `@Value("${auth-service.base-url}"):` Reads the `auth-service.base-url` property from the application configuration `application.yml` for the API's base URL.
    - Key Points:
      - `Error Handling:` Uses onStatus to handle 4xx and 5xx HTTP errors.
        - For 4xx errors, extracts a detailed error message using `extractDetailsFromError`.
        - Throws `UserNotFoundException` for user-related issues.
       
- One thing you will also notice is we are using `USERDTO class`, Yes
- In microservice application you create alot of `DTO'S` class objects to read data that is why it call **`Data Transfer Objects`** you can say it serve as user representative in this this service which consist both **`User model`** and **`UserRecord model`**

## What are the challenges encounter from the stating of the project?

- `Challenge(1)` Providing real-time updates for crypto prices and transactions, which requires efficient communication between the backend and the front-end.
- `Challenge(2)` Supporting both local currency operations and some foreign currency swapping involves accurate and up-to-date exchange rate handling, which can be resource-intensive.
- `Challenge(3)` The platform must handle high traffic and complex operations like trading, wallet transactions, and blockchain calls without degrading performance.
- `Challenge(4)` Ensuring compliance with local and international regulations for payment systems.
- `Challenge(5)` Detecting Sophisticated Fraud Patterns- Fraudsters often use complex strategies that blend legitimate and illegitimate activities, making detection difficult.

## How were i able to overcome it?

- `Solution(1)` Use WebSockets or server-sent events for real-time updates.
- `Solution(2)` Leverage flutterWave APIs for real-time exchange rates like naira-to-dollar, dollar-to-euro, naira-to-pounds exchange and implement robust error handling to deal with third-party API failures.
- `Solution(3)` Use microservices architecture, load balancing, and database optimization. Incorporate caching for frequently accessed data and monitor system performance.
- `Solution(4)` Design the system with secure data storage, encryption, and audit trails. Regularly update policies to align with evolving regulations
- `Solution(5)` Created middleware for advance security fraud detechtion:
    - Implemented middleware for IP address monitoring and transaction interception based on several criteria
         - **Large Transactions:** Flagged transactions exceeding platform-defined thresholds for further review.
         - **High-Frequency Transactions:** Monitored accounts for unusually high transaction volumes within short time-frames to detect suspicious behavior or could indicate potential money laundering or illegal activity.
         - **Geographic and Risk-Based Monitoring:**  Identified transactions involving high-risk regions/countries or blacklisted wallet addresses to comply with AML regulations.
         - **Behavioral Analysis:** Detected inconsistent behavior, such as large deviations from typical transaction amounts, to prevent fraud.
         - **Multiple Accounts Sharing the Same IP:** Checked for potential sybil attacks by monitoring accounts initiating transactions from the same IP address.
             - **Reason:**
                 - This could be a sign of suspicious activity such as a single entity controlling multiple accounts.
        - **Deposits Followed by Immediate Transfers:** Flagged immediate fund transfers after deposits to prevent potential money laundering activities.
            - **Reason:**
                - This behavior could indicate attempts to obfuscate the origin of the funds (layering phase of money laundering).
        - Implement data Encryption.
        - Implement Event Sourcing to make history difficult to tamper.
