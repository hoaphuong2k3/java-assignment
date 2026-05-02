package com.example.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Member {

    public enum Status { ACTIVE, EXPIRED, SUSPENDED }

    private int id;
    private String memberCode;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private LocalDate joinDate;
    private LocalDate expiryDate;
    private Status status;
    /** Số lần mất sách (LOST); reset khi mở khóa thẻ có phí. */
    private int lostBookCount;
    /** Soft delete: ẩn khỏi danh sách, không xóa hàng DB. */
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Member() {}

    public Member(String memberCode, String fullName, String email, String phone,
                  String address, LocalDate joinDate, LocalDate expiryDate) {
        this.memberCode = memberCode;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.joinDate = joinDate;
        this.expiryDate = expiryDate;
        this.status = Status.ACTIVE;
    }

    public boolean isCardValid() {
        return status == Status.ACTIVE && expiryDate != null
               && !LocalDate.now().isAfter(expiryDate);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMemberCode() { return memberCode; }
    public void setMemberCode(String memberCode) { this.memberCode = memberCode; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getLostBookCount() { return lostBookCount; }
    public void setLostBookCount(int lostBookCount) { this.lostBookCount = lostBookCount; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return memberCode + " - " + fullName;
    }
}
