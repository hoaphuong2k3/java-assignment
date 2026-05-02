package com.example.service;

import com.example.dao.BookDAO;
import com.example.dao.BorrowRecordDAO;
import com.example.dao.FineDAO;
import com.example.dao.MemberDAO;
import com.example.model.Book;
import com.example.model.BorrowRecord;
import com.example.model.Fine;
import com.example.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BorrowServiceTest {

    @Mock private BorrowRecordDAO borrowDAO;
    @Mock private BookDAO bookDAO;
    @Mock private MemberDAO memberDAO;
    @Mock private FineDAO fineDAO;

    @InjectMocks
    private BorrowService borrowService;

    private Member activeMember;
    private Book availableBook;

    @BeforeEach
    void setUp() {
        activeMember = new Member();
        activeMember.setId(1);
        activeMember.setMemberCode("M001");
        activeMember.setFullName("Nguyen Van A");
        activeMember.setStatus(Member.Status.ACTIVE);
        activeMember.setExpiryDate(LocalDate.now().plusYears(1));

        availableBook = new Book();
        availableBook.setId(10);
        availableBook.setTitle("Java Programming");
        availableBook.setAuthor("Author");
        availableBook.setTotalCopies(2);
        availableBook.setAvailableCopies(2);
    }

    // ---- borrowBook ----

    @Test
    void borrowBook_happyPath_savesRecord_withoutTouchingStoredAvailability() {
        when(memberDAO.findById(1)).thenReturn(Optional.of(activeMember));
        when(bookDAO.findById(10)).thenReturn(Optional.of(availableBook));
        when(borrowDAO.findByMember(1)).thenReturn(List.of());

        BorrowRecord result = borrowService.borrowBook(1, 10, 14);

        assertNotNull(result);
        assertEquals(1, result.getMemberId());
        assertEquals(10, result.getBookId());
        assertEquals(BorrowRecord.Status.BORROWING, result.getStatus());
        verify(borrowDAO).save(result);
    }

    @Test
    void borrowBook_defaultLoanDays_uses14Days() {
        when(memberDAO.findById(1)).thenReturn(Optional.of(activeMember));
        when(bookDAO.findById(10)).thenReturn(Optional.of(availableBook));
        when(borrowDAO.findByMember(1)).thenReturn(List.of());

        BorrowRecord result = borrowService.borrowBook(1, 10);

        LocalDate expectedDue = LocalDate.now().plusDays(BorrowService.DEFAULT_LOAN_DAYS);
        assertEquals(expectedDue, result.getDueDate());
    }

    @Test
    void borrowBook_memberNotFound_throwsIllegalArgument() {
        when(memberDAO.findById(99)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> borrowService.borrowBook(99, 10, 14));
        verify(borrowDAO, never()).save(any());
    }

    @Test
    void borrowBook_expiredMemberCard_throwsIllegalState() {
        activeMember.setExpiryDate(LocalDate.now().minusDays(1));
        when(memberDAO.findById(1)).thenReturn(Optional.of(activeMember));

        assertThrows(IllegalStateException.class, () -> borrowService.borrowBook(1, 10, 14));
        verify(borrowDAO, never()).save(any());
    }

    @Test
    void borrowBook_suspendedMember_throwsIllegalState() {
        activeMember.setStatus(Member.Status.SUSPENDED);
        when(memberDAO.findById(1)).thenReturn(Optional.of(activeMember));

        assertThrows(IllegalStateException.class, () -> borrowService.borrowBook(1, 10, 14));
    }

    @Test
    void borrowBook_bookNotFound_throwsIllegalArgument() {
        when(memberDAO.findById(1)).thenReturn(Optional.of(activeMember));
        when(bookDAO.findById(99)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> borrowService.borrowBook(1, 99, 14));
    }

    @Test
    void borrowBook_noAvailableCopies_throwsIllegalState() {
        availableBook.setAvailableCopies(0);
        when(memberDAO.findById(1)).thenReturn(Optional.of(activeMember));
        when(bookDAO.findById(10)).thenReturn(Optional.of(availableBook));

        assertThrows(IllegalStateException.class, () -> borrowService.borrowBook(1, 10, 14));
    }

    @Test
    void borrowBook_memberAlreadyBorrowingSameBook_throwsIllegalState() {
        BorrowRecord existing = new BorrowRecord(1, 10, LocalDate.now().minusDays(3),
                LocalDate.now().plusDays(11));
        // status defaults to BORROWING in constructor

        when(memberDAO.findById(1)).thenReturn(Optional.of(activeMember));
        when(bookDAO.findById(10)).thenReturn(Optional.of(availableBook));
        when(borrowDAO.findByMember(1)).thenReturn(List.of(existing));

        assertThrows(IllegalStateException.class, () -> borrowService.borrowBook(1, 10, 14));
        verify(borrowDAO, never()).save(any());
    }

    // ---- returnBook ----

    @Test
    void returnBook_onTime_noFineCreated() {
        BorrowRecord record = new BorrowRecord(1, 10, LocalDate.now().minusDays(5),
                LocalDate.now().plusDays(9));
        record.setId(100);

        when(borrowDAO.findById(100)).thenReturn(Optional.of(record));

        Optional<Fine> fine = borrowService.returnBook(100);

        assertTrue(fine.isEmpty());
        assertEquals(BorrowRecord.Status.RETURNED, record.getStatus());
        verify(borrowDAO).update(record);
        verify(fineDAO, never()).save(any());
    }

    @Test
    void returnBook_overdue_fineCreatedWithCorrectAmount() {
        // Due 3 days ago
        BorrowRecord record = new BorrowRecord(1, 10,
                LocalDate.now().minusDays(17), LocalDate.now().minusDays(3));
        record.setId(101);

        when(borrowDAO.findById(101)).thenReturn(Optional.of(record));

        Optional<Fine> fine = borrowService.returnBook(101);

        assertTrue(fine.isPresent());
        // 3 overdue days × 5000 = 15000
        assertEquals(new BigDecimal("15000"), fine.get().getAmount());
        verify(fineDAO).save(any(Fine.class));
    }

    @Test
    void returnBook_alreadyReturned_throwsIllegalState() {
        BorrowRecord record = new BorrowRecord(1, 10, LocalDate.now().minusDays(5),
                LocalDate.now().plusDays(9));
        record.setStatus(BorrowRecord.Status.RETURNED);
        record.setId(200);

        when(borrowDAO.findById(200)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class, () -> borrowService.returnBook(200));
        verify(borrowDAO, never()).update(any());
    }

    @Test
    void returnBook_recordNotFound_throwsIllegalArgument() {
        when(borrowDAO.findById(999)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> borrowService.returnBook(999));
    }

    // ---- markAsLost ----

    @Test
    void markAsLost_activeBorrow_createsLostFineAndDecreasesCopies() {
        BorrowRecord record = new BorrowRecord(1, 10, LocalDate.now().minusDays(5),
                LocalDate.now().plusDays(9));
        record.setId(300);

        when(borrowDAO.findById(300)).thenReturn(Optional.of(record));

        Fine fine = borrowService.markAsLost(300);

        assertNotNull(fine);
        assertEquals(BorrowService.LOST_BOOK_FEE, fine.getAmount());
        assertEquals(BorrowRecord.Status.LOST, record.getStatus());
        verify(bookDAO).decreaseTotalCopies(10);
        verify(fineDAO).save(fine);
        verify(memberDAO).incrementLostBookCount(1);
        verify(memberDAO).suspendIfLostThreshold(1, BorrowService.LOST_BOOKS_SUSPEND_THRESHOLD);
    }

    @Test
    void markAsLost_overdueRecord_alsoCreatesLostFine() {
        BorrowRecord record = new BorrowRecord(1, 10,
                LocalDate.now().minusDays(20), LocalDate.now().minusDays(6));
        record.setId(301);
        record.setStatus(BorrowRecord.Status.OVERDUE);

        when(borrowDAO.findById(301)).thenReturn(Optional.of(record));

        Fine fine = borrowService.markAsLost(301);
        // 6 ngày quá hạn × 5000 + 200000 bồi thường
        assertEquals(new BigDecimal("230000"), fine.getAmount());
    }

    @Test
    void markAsLost_alreadyReturned_throwsIllegalState() {
        BorrowRecord record = new BorrowRecord(1, 10, LocalDate.now().minusDays(5),
                LocalDate.now().plusDays(9));
        record.setId(302);
        record.setStatus(BorrowRecord.Status.RETURNED);

        when(borrowDAO.findById(302)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class, () -> borrowService.markAsLost(302));
        verify(fineDAO, never()).save(any());
    }

    @Test
    void markAsLost_recordNotFound_throwsIllegalArgument() {
        when(borrowDAO.findById(999)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> borrowService.markAsLost(999));
    }

    // ---- search ----

    @Test
    void search_blankKeyword_returnsAll() {
        when(borrowDAO.findAll()).thenReturn(List.of());
        borrowService.search("  ");
        verify(borrowDAO).findAll();
        verify(borrowDAO, never()).search(any());
    }

    @Test
    void search_keyword_delegatesToDaoSearch() {
        when(borrowDAO.search("nguyen")).thenReturn(List.of());
        borrowService.search("nguyen");
        verify(borrowDAO).search("nguyen");
    }

    // ---- syncOverdueStatuses ----

    @Test
    void syncOverdueStatuses_delegatesToDao() {
        when(borrowDAO.markOverdueRecords()).thenReturn(5);
        int count = borrowService.syncOverdueStatuses();
        assertEquals(5, count);
    }
}
