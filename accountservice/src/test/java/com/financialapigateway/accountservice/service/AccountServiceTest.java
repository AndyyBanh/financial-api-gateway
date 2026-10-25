package com.financialapigateway.accountservice.service;

import com.financialapigateway.accountservice.entity.Account;
import com.financialapigateway.accountservice.entity.ProcessedTransaction;
import com.financialapigateway.accountservice.enumeration.Status;
import com.financialapigateway.accountservice.event.TransactionEvent;
import com.financialapigateway.accountservice.repository.AccountRepository;
import com.financialapigateway.accountservice.repository.ProcessedTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ProcessedTransactionRepository processedTransactionRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void shouldFlagAccountsDoNotExist() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(50f);

        when(this.accountRepository.findByAccountNumber(event.getSenderId())).thenReturn(Optional.empty());
        when(this.accountRepository.findByAccountNumber(event.getRecipientId())).thenReturn(Optional.empty());

        Status status = this.accountService.processTransaction(event);

        assertEquals(Status.FAILED, status);
        verify(this.processedTransactionRepository).save(argThat(processed ->
                processed.getTransactionId().equals(event.getTransactionId())
                && processed.getStatus() == Status.FAILED
        ));

        verify(this.accountRepository, never()).save(any());
    }

    @Test
    void shouldFlagInsufficientBalance() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(50f);

        Optional<Account> senderAcc = Optional.of(new Account());
        Optional<Account> receiverAcc = Optional.of(new Account());

        when(this.accountRepository.findByAccountNumber(event.getSenderId())).thenReturn(senderAcc);
        when(accountRepository.findByAccountNumber(event.getRecipientId())).thenReturn(receiverAcc);

        Account sender = senderAcc.get();
        sender.setBalance(0f);

        Status status = this.accountService.processTransaction(event);

        assertEquals(Status.FAILED, status);
        verify(this.processedTransactionRepository).save(argThat(processed ->
                processed.getTransactionId().equals(event.getTransactionId())
                && processed.getStatus() == Status.FAILED
        ));

        verify(this.accountRepository, never()).save(any());
    }

    @Test
    void shouldUpdateBalanceOnSuccess() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(50f);

        Optional<Account> senderAcc = Optional.of(new Account());
        Optional<Account> receiverAcc = Optional.of(new Account());

        when(this.accountRepository.findByAccountNumber(event.getSenderId())).thenReturn(senderAcc);
        when(accountRepository.findByAccountNumber(event.getRecipientId())).thenReturn(receiverAcc);

        Account sender = senderAcc.get();
        sender.setAccountNumber(event.getSenderId());
        sender.setBalance(100f);

        Account receiver = receiverAcc.get();
        receiver.setAccountNumber(event.getRecipientId());
        receiver.setBalance(0f);

        Status status = this.accountService.processTransaction(event);

        assertEquals(50f, sender.getBalance());
        assertEquals(50f, receiver.getBalance());


        verify(this.accountRepository).save(argThat(account ->
            account.getAccountNumber().equals(event.getSenderId())
            && account.getBalance() == 50f
        ));

        verify(this.accountRepository).save(argThat(account ->
           account.getAccountNumber().equals(event.getRecipientId())
           && account.getBalance() == 50f
        ));

        assertEquals(Status.COMPLETE, status);
        verify(this.processedTransactionRepository).save(argThat(processed ->
            processed.getTransactionId().equals(event.getTransactionId())
            && processed.getStatus() == Status.COMPLETE
        ));
    }

    @Test
    void shouldSkipAlreadyProcessedTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(50f);

        when(this.processedTransactionRepository.findById(event.getTransactionId()))
                .thenReturn(Optional.of(new ProcessedTransaction(event.getTransactionId(), Status.COMPLETE)));

        Status status = this.accountService.processTransaction(event);

        assertEquals(Status.COMPLETE, status);
        verify(this.accountRepository, never()).findByAccountNumber(any());
        verify(this.accountRepository, never()).save(any());
        verify(this.processedTransactionRepository, never()).save(any());
    }
}
