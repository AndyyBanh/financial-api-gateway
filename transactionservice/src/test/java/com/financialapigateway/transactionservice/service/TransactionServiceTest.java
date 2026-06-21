package com.financialapigateway.transactionservice.service;

import com.financialapigateway.transactionservice.dto.TransactionDto;
import com.financialapigateway.transactionservice.entity.Transaction;
import com.financialapigateway.transactionservice.enumeration.Status;
import com.financialapigateway.transactionservice.event.TransactionResultEvent;
import com.financialapigateway.transactionservice.exceptions.AccountNotFoundException;
import com.financialapigateway.transactionservice.exceptions.InsufficientBalanceException;
import com.financialapigateway.transactionservice.feignclient.AccountClient;
import com.financialapigateway.transactionservice.kafka.TransactionProducer;
import com.financialapigateway.transactionservice.repository.TransactionRepository;
import com.financialapigateway.transactionservice.response.AccountResponse;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

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

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void shouldThrowAccountNotFoundExceptionWhenAccountNotFound() {
        TransactionDto input = new TransactionDto();
        input.setSenderId("123");
        input.setRecipientId("321");

        when(this.accountClient.getAccountByAccountNumber(input.getSenderId()))
                .thenThrow(FeignException.NotFound.class);

        assertThrows(AccountNotFoundException.class, () -> this.transactionService.createTransaction(input));

        verify(this.transactionRepository, never()).save(any());
    }

    @Test
    void shouldThrowInsufficientBalanceException() {
        TransactionDto input = new TransactionDto();
        input.setSenderId("123");
        input.setRecipientId("321");
        input.setAmount(50f);

        AccountResponse sender = new AccountResponse();
        sender.setAccountNumber(input.getSenderId());
        sender.setBalance(0f);
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
        input.setAmount(50f);

        AccountResponse sender = new AccountResponse();
        sender.setAccountNumber(input.getSenderId());
        sender.setBalance(100f);
        AccountResponse recipient = new AccountResponse();
        recipient.setAccountNumber(input.getRecipientId());
        recipient.setBalance(0f);

        when(this.accountClient.getAccountByAccountNumber(input.getSenderId())).thenReturn(sender);
        when(this.accountClient.getAccountByAccountNumber(input.getRecipientId())).thenReturn(recipient);

        this.transactionService.createTransaction(input);

        verify(this.transactionRepository).save(argThat(transaction ->
                transaction.getSenderId().equals(input.getSenderId())
                && transaction.getRecipientId().equals(input.getRecipientId())
                && transaction.getAmount() == 50f
                && transaction.getStatus() == Status.PENDING
        ));

        verify(this.transactionProducer).send(argThat(event ->
                    event.getSenderId().equals(input.getSenderId())
                && event.getRecipientId().equals(input.getRecipientId())
                && event.getAmount() == 50f
                && event.getStatus() == Status.PENDING
        ));
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

        verify(this.transactionRepository).save(argThat(res ->
            res.getTransactionId().equals(event.getTransactionId())
            &&    res.getStatus() == Status.COMPLETE
        ));
    }
}
