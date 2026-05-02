package com.example.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Fine {

    private int id;
    /** Nullable when fine is phí thẻ đọc giả (gia hạn / mở khóa). */
    private Integer borrowRecordId;
    private Integer memberId;
    private BigDecimal amount;
    private String reason;
    private boolean paid;
    private LocalDate paidDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Joined fields for display
    private String memberName;
    private String memberCode;
    private String bookTitle;
    private LocalDate borrowDate;
    private LocalDate dueDate;
    private LocalDate returnDate;

    public Fine() {}

    /** Phí gắn với phiếu mượn (quá hạn, mất sách, …). */
    public static Fine forBorrowRecord(int borrowRecordId, BigDecimal amount, String reason) {
        Fine f = new Fine();
        f.setBorrowRecordId(borrowRecordId);
        f.setAmount(amount);
        f.setReason(reason);
        f.setPaid(false);
        return f;
    }

    /** Phí chỉ gắn đọc giả (gia hạn thẻ, mở khóa thẻ). */
    public static Fine forMember(int memberId, BigDecimal amount, String reason) {
        Fine f = new Fine();
        f.setMemberId(memberId);
        f.setAmount(amount);
        f.setReason(reason);
        f.setPaid(false);
        return f;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getBorrowRecordId() { return borrowRecordId; }
    public void setBorrowRecordId(Integer borrowRecordId) { this.borrowRecordId = borrowRecordId; }

    public Integer getMemberId() { return memberId; }
    public void setMemberId(Integer memberId) { this.memberId = memberId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }

    public String getMemberCode() { return memberCode; }
    public void setMemberCode(String memberCode) { this.memberCode = memberCode; }

    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }

    public LocalDate getBorrowDate() { return borrowDate; }
    public void setBorrowDate(LocalDate borrowDate) { this.borrowDate = borrowDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
}
