package com.financialapigateway.accountservice.service;

import com.financialapigateway.accountservice.entity.Account;
import com.financialapigateway.accountservice.entity.OutboxEvent;
import com.financialapigateway.accountservice.entity.ProcessedTransaction;
import com.financialapigateway.accountservice.enumeration.Status;
import com.financialapigateway.accountservice.event.TransactionEvent;
import com.financialapigateway.accountservice.repository.AccountRepository;
import com.financialapigateway.accountservice.repository.OutboxEventRepository;
import com.financialapigateway.accountservice.repository.ProcessedTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void shouldFlagAccountsDoNotExist() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("50"));

        when(this.accountRepository.findByAccountNumber(event.getSenderId())).thenReturn(Optional.empty());
        when(this.accountRepository.findByAccountNumber(event.getRecipientId())).thenReturn(Optional.empty());

        Status status = this.accountService.processTransaction(event);

        assertEquals(Status.FAILED, status);
        verify(this.processedTransactionRepository).save(new ProcessedTransaction(event.getTransactionId(), Status.FAILED));

        verify(this.accountRepository, never()).save(any());
    }

    @Test
    void shouldFlagInsufficientBalance() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("50"));

        Optional<Account> senderAcc = Optional.of(new Account());
        Optional<Account> receiverAcc = Optional.of(new Account());

        when(this.accountRepository.findByAccountNumber(event.getSenderId())).thenReturn(senderAcc);
        when(accountRepository.findByAccountNumber(event.getRecipientId())).thenReturn(receiverAcc);

        Account sender = senderAcc.get();
        sender.setBalance(new BigDecimal("0"));

        Status status = this.accountService.processTransaction(event);

        assertEquals(Status.FAILED, status);
        verify(this.processedTransactionRepository).save(new ProcessedTransaction(event.getTransactionId(), Status.FAILED));

        verify(this.accountRepository, never()).save(any());
    }

    @Test
    void shouldUpdateBalanceOnSuccess() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("50"));

        Optional<Account> senderAcc = Optional.of(new Account());
        Optional<Account> receiverAcc = Optional.of(new Account());

        when(this.accountRepository.findByAccountNumber(event.getSenderId())).thenReturn(senderAcc);
        when(accountRepository.findByAccountNumber(event.getRecipientId())).thenReturn(receiverAcc);

        Account sender = senderAcc.get();
        sender.setAccountNumber(event.getSenderId());
        sender.setBalance(new BigDecimal("100"));

        Account receiver = receiverAcc.get();
        receiver.setAccountNumber(event.getRecipientId());
        receiver.setBalance(new BigDecimal("0"));

        Status status = this.accountService.processTransaction(event);

        assertEquals(Status.COMPLETE, status);
        verify(this.accountRepository).save(accountWith(event.getSenderId(), new BigDecimal("50")));
        verify(this.accountRepository).save(accountWith(event.getRecipientId(), new BigDecimal("50")));
        verify(this.processedTransactionRepository).save(new ProcessedTransaction(event.getTransactionId(), Status.COMPLETE));
    }

    @Test
    void shouldFailNegativeAmount() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("-50"));

        Status status = this.accountService.processTransaction(event);

        assertEquals(Status.FAILED, status);
        verify(this.accountRepository, never()).findByAccountNumber(any());
        verify(this.accountRepository, never()).save(any());
        verify(this.processedTransactionRepository).save(new ProcessedTransaction(event.getTransactionId(), Status.FAILED));
    }

    @Test
    void shouldKeepCentsExactOnTransfer() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("0.10"));

        Optional<Account> senderAcc = Optional.of(new Account());
        Optional<Account> receiverAcc = Optional.of(new Account());

        when(this.accountRepository.findByAccountNumber(event.getSenderId())).thenReturn(senderAcc);
        when(accountRepository.findByAccountNumber(event.getRecipientId())).thenReturn(receiverAcc);

        Account sender = senderAcc.get();
        sender.setAccountNumber(event.getSenderId());
        sender.setBalance(new BigDecimal("0.30"));

        Account receiver = receiverAcc.get();
        receiver.setAccountNumber(event.getRecipientId());
        receiver.setBalance(new BigDecimal("0.20"));

        Status status = this.accountService.processTransaction(event);

        assertEquals(Status.COMPLETE, status);
        verify(this.accountRepository).save(accountWith(event.getSenderId(), new BigDecimal("0.20")));
        verify(this.accountRepository).save(accountWith(event.getRecipientId(), new BigDecimal("0.30")));
    }

    @Test
    void shouldSkipAlreadyProcessedTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("50"));

        when(this.processedTransactionRepository.findById(event.getTransactionId()))
                .thenReturn(Optional.of(new ProcessedTransaction(event.getTransactionId(), Status.COMPLETE)));

        Status status = this.accountService.processTransaction(event);

        assertEquals(Status.COMPLETE, status);
        verify(this.accountRepository, never()).findByAccountNumber(any());
        verify(this.accountRepository, never()).save(any());
        verify(this.processedTransactionRepository, never()).save(any());
    }

    @Test
    void shouldSaveOutboxEventWithCompleteStatus() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("50"));

        Account sender = new Account();
        sender.setAccountNumber(event.getSenderId());
        sender.setBalance(new BigDecimal("100"));

        Account receiver = new Account();
        receiver.setAccountNumber(event.getRecipientId());
        receiver.setBalance(new BigDecimal("0"));

        when(this.accountRepository.findByAccountNumber(event.getSenderId())).thenReturn(Optional.of(sender));
        when(this.accountRepository.findByAccountNumber(event.getRecipientId())).thenReturn(Optional.of(receiver));

        this.accountService.processTransaction(event);

        verify(this.outboxEventRepository).save(outboxEventWith(event.getTransactionId(), Status.COMPLETE));
    }

    @Test
    void shouldSaveOutboxEventWithFailedStatus() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("50"));

        when(this.accountRepository.findByAccountNumber(event.getSenderId())).thenReturn(Optional.empty());
        when(this.accountRepository.findByAccountNumber(event.getRecipientId())).thenReturn(Optional.empty());

        this.accountService.processTransaction(event);

        verify(this.outboxEventRepository).save(outboxEventWith(event.getTransactionId(), Status.FAILED));
    }

    @Test
    void shouldNotSaveOutboxEventForAlreadyProcessedTransaction() {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setSenderId("123");
        event.setRecipientId("321");
        event.setAmount(new BigDecimal("50"));

        when(this.processedTransactionRepository.findById(event.getTransactionId()))
                .thenReturn(Optional.of(new ProcessedTransaction(event.getTransactionId(), Status.COMPLETE)));

        this.accountService.processTransaction(event);

        verify(this.outboxEventRepository, never()).save(any());
    }

    private static Account accountWith(String accountNumber, BigDecimal balance) {
        return argThat(account -> account.getAccountNumber().equals(accountNumber) && account.getBalance().compareTo(balance) == 0);
    }

    private static OutboxEvent outboxEventWith(UUID transactionId, Status status) {
        return argThat(outbox -> outbox.getTransactionId().equals(transactionId) && outbox.getStatus() == status && outbox.getCreatedAt() != null);
    }
}
