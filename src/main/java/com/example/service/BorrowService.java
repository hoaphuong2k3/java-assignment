package com.example.service;

import com.example.dao.BookDAO;
import com.example.dao.BorrowRecordDAO;
import com.example.dao.FineDAO;
import com.example.dao.MemberDAO;
import com.example.model.Book;
import com.example.model.BorrowRecord;
import com.example.model.Fine;
import com.example.model.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

public class BorrowService {

    /** Fine rate: 5,000 VND per overdue day */
    public static final BigDecimal FINE_PER_DAY = new BigDecimal("5000");
    /** Replacement fee for a lost book */
    public static final BigDecimal LOST_BOOK_FEE = new BigDecimal("200000");
    /** Default loan period in days */
    public static final int DEFAULT_LOAN_DAYS = 14;
    /** Tối đa ~nửa tháng mượn */
    public static final int MAX_LOAN_DAYS = 15;
    /** Quá hạn quá số ngày này → tự động LOST */
    public static final int AUTO_LOST_AFTER_OVERDUE_DAYS = 30;
    /** Số lần mất sách để khóa thẻ */
    public static final int LOST_BOOKS_SUSPEND_THRESHOLD = 5;

    private final BorrowRecordDAO borrowDAO;
    private final BookDAO bookDAO;
    private final MemberDAO memberDAO;
    private final FineDAO fineDAO;

    public BorrowService() {
        this.borrowDAO = new BorrowRecordDAO();
        this.bookDAO = new BookDAO();
        this.memberDAO = new MemberDAO();
        this.fineDAO = new FineDAO();
    }

    /** Package-private constructor for testing. */
    BorrowService(BorrowRecordDAO borrowDAO, BookDAO bookDAO, MemberDAO memberDAO, FineDAO fineDAO) {
        this.borrowDAO = borrowDAO;
        this.bookDAO = bookDAO;
        this.memberDAO = memberDAO;
        this.fineDAO = fineDAO;
    }

    public List<BorrowRecord> getAllRecords() {
        return borrowDAO.findAll();
    }

    public List<BorrowRecord> getActiveBorrows() {
        return borrowDAO.findActiveBorrows();
    }

    public List<BorrowRecord> getOverdueRecords() {
        return borrowDAO.findOverdue();
    }

    public List<BorrowRecord> getBorrowsByMember(int memberId) {
        return borrowDAO.findByMember(memberId);
    }

    public List<BorrowRecord> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return borrowDAO.findAll();
        return borrowDAO.search(keyword.trim());
    }

    /**
     * Đặt hạn trả cụ thể. Khoảng cách từ hôm nay đến hạn trả: 1..{@link #MAX_LOAN_DAYS} ngày.
     */
    public BorrowRecord borrowBook(int memberId, int bookId, LocalDate dueDate) {
        Member member = memberDAO.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Đọc giả không tìm thấy."));
        if (!member.isCardValid())
            throw new IllegalStateException("Thẻ đọc giả hết hạn hoặc bị khóa. Vui lòng gia hạn / mở khóa thẻ.");

        Book book = bookDAO.findById(bookId)
            .orElseThrow(() -> new IllegalArgumentException("Sách không tìm thấy."));
        if (!book.isAvailable())
            throw new IllegalStateException("Sách '" + book.getTitle() + "' hiện không có sẵn.");

        LocalDate today = LocalDate.now();
        long loanDays = ChronoUnit.DAYS.between(today, dueDate);
        if (loanDays < 1 || loanDays > MAX_LOAN_DAYS) {
            throw new IllegalArgumentException(
                "Ngày trả dự kiến phải trong khoảng 1–" + MAX_LOAN_DAYS + " ngày kể từ hôm nay.");
        }

        List<BorrowRecord> existing = borrowDAO.findByMember(memberId);
        boolean alreadyBorrowed = existing.stream().anyMatch(r ->
            r.getBookId() == bookId
                && (r.getStatus() == BorrowRecord.Status.BORROWING
                    || r.getStatus() == BorrowRecord.Status.OVERDUE));
        if (alreadyBorrowed)
            throw new IllegalStateException("Đọc giả đang mượn cuốn sách này (hoặc đang quá hạn).");

        BorrowRecord record = new BorrowRecord(memberId, bookId, today, dueDate);
        borrowDAO.save(record);
        return record;
    }

    /** @param loanDays số ngày mượn tính từ hôm nay (1..{@link #MAX_LOAN_DAYS}) */
    public BorrowRecord borrowBook(int memberId, int bookId, int loanDays) {
        if (loanDays < 1 || loanDays > MAX_LOAN_DAYS) {
            throw new IllegalArgumentException(
                "Số ngày mượn phải từ 1 đến " + MAX_LOAN_DAYS + " ngày.");
        }
        return borrowBook(memberId, bookId, LocalDate.now().plusDays(loanDays));
    }

    public BorrowRecord borrowBook(int memberId, int bookId) {
        return borrowBook(memberId, bookId, DEFAULT_LOAN_DAYS);
    }

    /**
     * Gia hạn mượn: chỉ BORROWING; tổng số ngày từ ngày mượn đến hạn mới ≤ {@link #MAX_LOAN_DAYS}.
     */
    public void extendBorrow(int borrowRecordId, LocalDate newDueDate) {
        BorrowRecord r = borrowDAO.findById(borrowRecordId)
            .orElseThrow(() -> new IllegalArgumentException("Phiếu mượn không tồn tại."));
        if (r.getStatus() != BorrowRecord.Status.BORROWING) {
            throw new IllegalStateException("Chỉ gia hạn khi đang mượn (chưa quá hạn).");
        }
        if (!newDueDate.isAfter(r.getDueDate())) {
            throw new IllegalArgumentException("Hạn trả mới phải sau hạn hiện tại.");
        }
        if (newDueDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Hạn trả mới không được trước hôm nay.");
        }
        long totalSpan = ChronoUnit.DAYS.between(r.getBorrowDate(), newDueDate);
        if (totalSpan > MAX_LOAN_DAYS) {
            throw new IllegalArgumentException(
                "Tổng thời gian mượn không vượt quá " + MAX_LOAN_DAYS + " ngày kể từ ngày mượn.");
        }
        borrowDAO.updateDueDate(borrowRecordId, newDueDate);
        r.setDueDate(newDueDate);
    }

    public Optional<Fine> returnBook(int borrowRecordId) {
        BorrowRecord record = borrowDAO.findById(borrowRecordId)
            .orElseThrow(() -> new IllegalArgumentException("Phiếu mượn không tồn tại."));
        if (record.getStatus() == BorrowRecord.Status.RETURNED)
            throw new IllegalStateException("Sách này đã được trả rồi.");

        record.setReturnDate(LocalDate.now());
        record.setStatus(BorrowRecord.Status.RETURNED);
        borrowDAO.update(record);

        Fine fine = null;
        long overdueDays = record.getOverdueDays();
        if (overdueDays > 0) {
            BigDecimal fineAmount = FINE_PER_DAY.multiply(BigDecimal.valueOf(overdueDays));
            fine = Fine.forBorrowRecord(record.getId(), fineAmount,
                "Trả sách trễ " + overdueDays + " ngày (" + FINE_PER_DAY.toPlainString() + "đ/ngày)");
            fineDAO.save(fine);
        }
        return Optional.ofNullable(fine);
    }

    /**
     * Đánh dấu mất sách: một phiếu phạt gồm phí quá hạn (nếu có) + bồi thường 200k; tăng lost_book_count; có thể khóa thẻ.
     */
    public Fine markAsLost(int borrowRecordId) {
        BorrowRecord record = borrowDAO.findById(borrowRecordId)
            .orElseThrow(() -> new IllegalArgumentException("Phiếu mượn không tồn tại."));
        return finalizeLost(record, null);
    }

    /**
     * Phiếu OVERDUE quá {@link #AUTO_LOST_AFTER_OVERDUE_DAYS} ngày → LOST + ghi chú (email/biên bản tượng trưng).
     */
    public int syncLongOverdueToLost() {
        List<BorrowRecord> list = borrowDAO.findOverdueBeyondDays(AUTO_LOST_AFTER_OVERDUE_DAYS);
        String extraNote = """
            Tự động chuyển Mất sách (quá hạn trên 30 ngày không trả). \
            Thông báo đã gửi về email đọc giả (tượng trưng). Biên bản đã gửi lên trường.""";
        int n = 0;
        for (BorrowRecord r : list) {
            finalizeLost(r, extraNote);
            n++;
        }
        return n;
    }

    private Fine finalizeLost(BorrowRecord record, String notePrefix) {
        if (record.getStatus() == BorrowRecord.Status.RETURNED)
            throw new IllegalStateException("Phiếu mượn đã được đóng.");
        if (record.getStatus() == BorrowRecord.Status.LOST)
            throw new IllegalStateException("Phiếu đã được ghi nhận mất sách.");

        long overdueDays = record.getOverdueDays();
        BigDecimal overduePart = overdueDays > 0
            ? FINE_PER_DAY.multiply(BigDecimal.valueOf(overdueDays))
            : BigDecimal.ZERO;
        BigDecimal total = overduePart.add(LOST_BOOK_FEE);

        StringBuilder noteBlock = new StringBuilder();
        if (notePrefix != null && !notePrefix.isBlank())
            noteBlock.append(notePrefix.trim()).append("\n");
        if (overdueDays > 0) {
            noteBlock.append("Phí phạt quá hạn (").append(overdueDays).append(" ngày): ")
                .append(overduePart.toPlainString()).append("đ. ");
        }
        noteBlock.append("Bồi thường mất sách: ").append(LOST_BOOK_FEE.toPlainString()).append("đ.");

        String oldNotes = record.getNotes();
        record.setNotes((oldNotes != null && !oldNotes.isBlank() ? oldNotes + "\n" : "") + noteBlock);

        String reason = noteBlock.toString().replace('\n', ' ');
        if (reason.length() > 250) reason = reason.substring(0, 247) + "...";

        record.setStatus(BorrowRecord.Status.LOST);
        borrowDAO.update(record);
        bookDAO.decreaseTotalCopies(record.getBookId());

        Fine fine = Fine.forBorrowRecord(record.getId(), total, reason);
        fineDAO.save(fine);

        memberDAO.incrementLostBookCount(record.getMemberId());
        memberDAO.suspendIfLostThreshold(record.getMemberId(), LOST_BOOKS_SUSPEND_THRESHOLD);

        return fine;
    }

    public int syncOverdueStatuses() {
        return borrowDAO.markOverdueRecords();
    }

    public long countActiveBorrows() {
        return borrowDAO.countActiveBorrows();
    }

    public long countOverdue() {
        return borrowDAO.countOverdue();
    }
}
