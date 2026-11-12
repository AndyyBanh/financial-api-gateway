package com.financialapigateway.transactionservice.service;

import com.financialapigateway.transactionservice.dto.TransactionDto;
import com.financialapigateway.transactionservice.entity.Transaction;
import com.financialapigateway.transactionservice.enumeration.Status;
import com.financialapigateway.transactionservice.event.TransactionEvent;
import com.financialapigateway.transactionservice.event.TransactionResultEvent;
import com.financialapigateway.transactionservice.exceptions.AccountNotFoundException;
import com.financialapigateway.transactionservice.exceptions.InsufficientBalanceException;
import com.financialapigateway.transactionservice.exceptions.InvalidAmountException;
import com.financialapigateway.transactionservice.feignclient.AccountClient;
import com.financialapigateway.transactionservice.kafka.TransactionProducer;
import com.financialapigateway.transactionservice.repository.TransactionRepository;
import com.financialapigateway.transactionservice.response.AccountResponse;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionProducer transactionProducer;

    @Mock
    private AccountClient accountClient;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        // Same-thread executor so the async recipient lookup runs inline in tests
        this.transactionService = new TransactionService(this.transactionRepository, this.transactionProducer, this.accountClient, Runnable::run);
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenAccountNotFound() {
        TransactionDto input = new TransactionDto();
        input.setSenderId("123");
        input.setRecipientId("321");
        input.setAmount(new BigDecimal("50"));

        when(this.accountClient.getAccountByAccountNumber(input.getSenderId()))
                .thenThrow(FeignException.NotFound.class);
        // Both lookups now start together, so the recipient is looked up too
        when(this.accountClient.getAccountByAccountNumber(input.getRecipientId())).thenReturn(new AccountResponse());

        assertThrows(AccountNotFoundException.class, () -> this.transactionService.createTransaction(input));

        verify(this.transactionRepository, never()).save(any());
    }

    @Test
    void shouldThrowInsufficientBalanceException() {
        TransactionDto input = new TransactionDto();
        input.setSenderId("123");
        input.setRecipientId("321");
        input.setAmount(new BigDecimal("50"));

        AccountResponse sender = new AccountResponse();
        sender.setAccountNumber(input.getSenderId());
        sender.setBalance(new BigDecimal("0"));
        AccountResponse recipient = new AccountResponse();
        recipient.setAccountNumber(input.getRecipientId());

        when(this.accountClient.getAccountByAccountNumber(input.getSenderId())).thenReturn(sender);
        when(this.accountClient.getAccountByAccountNumber(input.getRecipientId())).thenReturn(recipient);

        assertThrows(InsufficientBalanceException.class,
                () -> this.transactionService.createTransaction(input));

        verify(this.transactionRepository, never()).save(any());
    }

    @Test
    void shouldCreateTransaction() {
        TransactionDto input = new TransactionDto();
        input.setSenderId("123");
        input.setRecipientId("321");
        input.setAmount(new BigDecimal("50"));

        AccountResponse sender = new AccountResponse();
        sender.setAccountNumber(input.getSenderId());
        sender.setBalance(new BigDecimal("100"));
        AccountResponse recipient = new AccountResponse();
        recipient.setAccountNumber(input.getRecipientId());
        recipient.setBalance(new BigDecimal("0"));

        when(this.accountClient.getAccountByAccountNumber(input.getSenderId())).thenReturn(sender);
        when(this.accountClient.getAccountByAccountNumber(input.getRecipientId())).thenReturn(recipient);

        this.transactionService.createTransaction(input);

        verify(this.transactionRepository).save(pendingTransactionFor(input));
        verify(this.transactionProducer).send(pendingEventFor(input));
    }
    
    @Test
    void shouldRejectNegativeAmount() {
        assertAmountRejected(new BigDecimal("-50"), "amount must be greater than 0");
    }

    @Test
    void shouldRejectZeroAmount() {
        assertAmountRejected(new BigDecimal("0"), "amount must be greater than 0");
    }

    @Test
    void shouldRejectMissingAmount() {
        assertAmountRejected(null, "amount is required");
    }

    @Test
    void shouldRejectMoreThanTwoDecimalPlaces() {
        assertAmountRejected(new BigDecimal("0.005"), "amount must have at most 2 decimal places");
    }

    @Test
    void shouldRejectAmountTooLarge() {
        assertAmountRejected(new BigDecimal("123456789012345678"), "amount is too large");
    }

    @Test
    void shouldAcceptAmountWithTrailingZero() {
        TransactionDto input = new TransactionDto();
        input.setSenderId("123");
        input.setRecipientId("321");
        input.setAmount(new BigDecimal("50.10"));

        AccountResponse sender = new AccountResponse();
        sender.setAccountNumber(input.getSenderId());
        sender.setBalance(new BigDecimal("100"));
        AccountResponse recipient = new AccountResponse();
        recipient.setAccountNumber(input.getRecipientId());
        recipient.setBalance(new BigDecimal("0"));

        when(this.accountClient.getAccountByAccountNumber(input.getSenderId())).thenReturn(sender);
        when(this.accountClient.getAccountByAccountNumber(input.getRecipientId())).thenReturn(recipient);

        this.transactionService.createTransaction(input);

        verify(this.transactionRepository).save(pendingTransactionFor(input));
        verify(this.transactionProducer).send(pendingEventFor(input));
    }

    @Test
    void shouldNotUpdateTransaction() {
        TransactionResultEvent event = new TransactionResultEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setStatus(Status.COMPLETE);

        when(this.transactionRepository.findById(event.getTransactionId())).thenReturn(Optional.empty());

        this.transactionService.updateTransactionStatus(event);

        verify(this.transactionRepository, never()).save(any());
    }

    @Test
    void shouldUpdateTransaction() {
        TransactionResultEvent event = new TransactionResultEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setStatus(Status.COMPLETE);

        Optional<Transaction> transactionOptional = Optional.of(new Transaction());
        when(this.transactionRepository.findById(event.getTransactionId())).thenReturn(transactionOptional);

        Transaction transaction = transactionOptional.get();
        transaction.setTransactionId(event.getTransactionId());
        transaction.setStatus(Status.PENDING);

        this.transactionService.updateTransactionStatus(event);

        verify(this.transactionRepository).save(transactionWith(event.getTransactionId(), Status.COMPLETE));
    }

    @Test
    void shouldNotOverwriteFinalizedTransaction() {
        TransactionResultEvent event = new TransactionResultEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setStatus(Status.FAILED);

        Transaction transaction = new Transaction();
        transaction.setTransactionId(event.getTransactionId());
        transaction.setStatus(Status.COMPLETE);
        when(this.transactionRepository.findById(event.getTransactionId())).thenReturn(Optional.of(transaction));

        this.transactionService.updateTransactionStatus(event);

        assertEquals(Status.COMPLETE, transaction.getStatus());
        verify(this.transactionRepository, never()).save(any());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenRecipientNotFound() {
        TransactionDto input = new TransactionDto();
        input.setSenderId("123");
        input.setRecipientId("321");
        input.setAmount(new BigDecimal("50"));

        when(this.accountClient.getAccountByAccountNumber(input.getSenderId())).thenReturn(new AccountResponse());
        when(this.accountClient.getAccountByAccountNumber(input.getRecipientId()))
                .thenThrow(FeignException.NotFound.class);

        assertThrows(AccountNotFoundException.class, () -> this.transactionService.createTransaction(input));

        verify(this.transactionRepository, never()).save(any());
    }

    @Test
    void shouldCountSentTransactionsSince() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(10);
        when(this.transactionRepository.countBySenderIdAndCreatedAtAfter("123", since)).thenReturn(7L);

        assertEquals(7L, this.transactionService.countSentTransactionsSince("123", since));
    }

    private void assertAmountRejected(BigDecimal amount, String expectedMessage) {
        TransactionDto input = new TransactionDto();
        input.setSenderId("123");
        input.setRecipientId("321");
        input.setAmount(amount);

        InvalidAmountException ex = assertThrows(InvalidAmountException.class,
                () -> this.transactionService.createTransaction(input));

        assertEquals(expectedMessage, ex.getMessage());
        verifyNoInteractions(this.accountClient);
        verify(this.transactionRepository, never()).save(any());
        verify(this.transactionProducer, never()).send(any());
    }

    private static Transaction pendingTransactionFor(TransactionDto input) {
        return argThat(transaction -> transaction.getSenderId().equals(input.getSenderId())
                && transaction.getRecipientId().equals(input.getRecipientId())
                && transaction.getAmount().compareTo(input.getAmount()) == 0
                && transaction.getStatus() == Status.PENDING);
    }

    private static TransactionEvent pendingEventFor(TransactionDto input) {
        return argThat(event -> event.getSenderId().equals(input.getSenderId())
                && event.getRecipientId().equals(input.getRecipientId())
                && event.getAmount().compareTo(input.getAmount()) == 0
                && event.getStatus() == Status.PENDING);
    }

    private static Transaction transactionWith(UUID transactionId, Status status) {
        return argThat(transaction -> transaction.getTransactionId().equals(transactionId) && transaction.getStatus() == status);
    }
}
