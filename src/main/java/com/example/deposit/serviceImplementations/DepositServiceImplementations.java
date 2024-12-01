package com.example.deposit.serviceImplementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import com.example.deposit.dto.UserDTO;
import com.example.deposit.dto.UserRecordDTO;
import com.example.deposit.enums.TransactionMessage;
import com.example.deposit.enums.TransactionStatus;
import com.example.deposit.enums.TransactionType;
import com.example.deposit.messageProducer.WalletNotificationProducer;
import com.example.deposit.models.BankList;
import com.example.deposit.models.Wallet;
import com.example.deposit.models.WalletTransactionHistory;
import com.example.deposit.payloads.DepositRequest;
import com.example.deposit.repository.BankListRepository;
import com.example.deposit.repository.WalletRepository;
import com.example.deposit.repository.WalletTransanctionHistoryRepository;
import com.example.deposit.services.DepositService;
import com.example.deposit.utils.KeyCollections;
import com.example.deposit.utils.Refactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.deposit.responses.Error;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepositServiceImplementations implements DepositService{

    private final WalletRepository walletRepository;
    private final WalletTransanctionHistoryRepository walletTransanctionHistoryRepository;
    private final UserServiceClient userServiceClient;
    private final BankListRepository bankListRepository;
    private final PaystackServiceImpl paystackService;
    private final KeyCollections keyCollections;
    private final WalletNotificationProducer walletNotificationProducer;

    @Override
    public ResponseEntity<?> createDeposit(DepositRequest request, String token, Authentication authentication) {
        String username = authentication.getName(); 
        UserDTO user = userServiceClient.getUserByUsername(username, token);
        Optional<Wallet> existingWalletOptional = walletRepository.findByUserId(user.getId());
        Optional<BankList> checkBankBelongToRequestUser = bankListRepository.findByAccountNumber(request.getAccountNumber());
        if (checkBankBelongToRequestUser.isPresent()) {
            Long userId = Long.parseLong(request.getUserId());
            if (checkBankBelongToRequestUser.get().getUserId().equals(userId) && user.getId().equals(userId)
                    && request.getAccountNumber().equals(checkBankBelongToRequestUser.get().getAccountNumber())
                    && request.getBankCode().equals(checkBankBelongToRequestUser.get().getBankCode())) {

                Wallet wallet;
                if (existingWalletOptional.isPresent()) {
                    wallet = existingWalletOptional.get();
                    BigDecimal newBalance = wallet.getBalance().add(request.getAmount());
                    wallet.setBalance(newBalance);
                } else {
                    // if user does not have account of wallet where user balance is store, then create one
                    wallet = new Wallet();
                    wallet.setUserId(user.getId());
                    wallet.setBalance(request.getAmount());
                }
                try {
                    // Initialize payment with Paystack
                    String paymentResponse = paystackService
                            .initializePayment(user.getEmail(), request.getAmount().intValue() * 100).block();
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(paymentResponse);
                    String authorizationUrl = rootNode.path("data").path("authorization_url").asText();
                    // Save userAuthenticationDetailsRecord
                    Long uniqueId = keyCollections.createSnowflakeUniqueId();
                    // Create and save the transaction
                    String formattedTransactionType = Refactory.formatEnumValue(TransactionType.BANK_TO_WALLET_DEPOST);

                    WalletTransactionHistory transaction = new WalletTransactionHistory();
                    transaction.setId(uniqueId);
                    transaction.setWallet(wallet);
                    transaction.setSessionId(keyCollections.generateSessionId());
                    transaction.setAmount(request.getAmount());
                    transaction.setType(request.getType());
                    transaction.setDescription(formattedTransactionType);
                    transaction.setMessage(TransactionMessage.Successful.toString());
                    transaction.setStatus(TransactionStatus.Success.toString());
                    // Save the wallet
                    walletRepository.save(wallet);
                    // save history
                    walletTransanctionHistoryRepository.save(transaction);

                    UserDTO fromUser = userServiceClient.getUserById(userId, token);

                    String senderEmail = fromUser.getEmail();
                    LocalDateTime transactionTime = LocalDateTime.now();

                    // Extract firstName and lastName separately from the UserRecordDTO list
                    String firstName = fromUser.getRecords().stream()
                            .findFirst()
                            .map(UserRecordDTO::getFirstName)
                            .orElse("Unknown");

                    // notification
                    walletNotificationProducer.sendDepositWalletNotification(senderEmail, firstName, request.getAmount(), transactionTime, existingWalletOptional.get().getBalance());

                    return ResponseEntity.status(HttpStatus.CREATED).body(authorizationUrl);
                } catch (JsonProcessingException e) {
                    return Error.createResponse("Payment initialization failed",
                            HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                }
            } else {
                return Error.createResponse("Sorry..! Invalid Card Details",
                        HttpStatus.FORBIDDEN,
                        "The card deposit details you process for deposit does not match any of your card details in the system.");
            }
           
        }
        return Error.createResponse("Sorry..! fraudulent attempt detect on this request.",
                HttpStatus.FORBIDDEN,
                "The Bank account you're attempting on moving money from does not belong to you.\n Any one more attempt your account will be banned and we will send your profile to the anti-crime agency.");
    }

    public static String capitalizeFirstLetter(String input) {
        return input == null || input.isEmpty()
                ? input
                : input.transform(s -> s.substring(0, 1).toUpperCase() + s.substring(1));
    }

}