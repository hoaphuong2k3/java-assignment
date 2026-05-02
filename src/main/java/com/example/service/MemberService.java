package com.example.service;

import com.example.dao.FineDAO;
import com.example.dao.MemberDAO;
import com.example.model.Fine;
import com.example.model.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class MemberService {

    public static final BigDecimal RENEW_CARD_FEE = new BigDecimal("100000");
    public static final BigDecimal UNLOCK_CARD_FEE = new BigDecimal("50000");
    /** Đăng ký / gia hạn thẻ có phí: thời hạn thêm (năm) */
    public static final int MEMBERSHIP_EXTENSION_YEARS = 2;

    private final MemberDAO memberDAO;
    private final FineDAO fineDAO;

    public MemberService() {
        this.memberDAO = new MemberDAO();
        this.fineDAO = new FineDAO();
    }

    /** Package-private constructor for testing. */
    MemberService(MemberDAO memberDAO) {
        this.memberDAO = memberDAO;
        this.fineDAO = new FineDAO();
    }

    MemberService(MemberDAO memberDAO, FineDAO fineDAO) {
        this.memberDAO = memberDAO;
        this.fineDAO = fineDAO;
    }

    public List<Member> getAllMembers() {
        return memberDAO.findAll();
    }

    public List<Member> searchMembers(String keyword) {
        if (keyword == null || keyword.isBlank()) return memberDAO.findAll();
        return memberDAO.search(keyword.trim());
    }

    public Optional<Member> getMemberById(int id) {
        return memberDAO.findById(id);
    }

    public Optional<Member> getMemberByCode(String code) {
        return memberDAO.findByCode(code);
    }

    /**
     * Đăng ký mới: miễn phí, thẻ {@value #MEMBERSHIP_EXTENSION_YEARS} năm kể từ hôm nay.
     */
    public void addMember(Member member) {
        validateMember(member);
        member.setMemberCode(memberDAO.generateNextMemberCode());
        member.setJoinDate(LocalDate.now());
        member.setExpiryDate(LocalDate.now().plusYears(MEMBERSHIP_EXTENSION_YEARS));
        member.setStatus(Member.Status.ACTIVE);
        member.setLostBookCount(0);
        memberDAO.save(member);
    }

    /** Sửa thông tin cá nhân (không đổi hạn thẻ / trạng thái qua form này). */
    public void updateMember(Member member) {
        validateMember(member);
        memberDAO.findById(member.getId())
            .orElseThrow(() -> new IllegalArgumentException("Đọc giả không tồn tại."));
        memberDAO.updatePersonalInfo(member.getId(),
            member.getFullName(), nullToEmpty(member.getEmail()),
            nullToEmpty(member.getPhone()), nullToEmpty(member.getAddress()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Chỉ cho đọc giả hết hạn (EXPIRED). */
    public void deleteMember(int id) {
        Member m = memberDAO.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Đọc giả không tồn tại hoặc đã được ẩn."));
        if (m.getStatus() != Member.Status.EXPIRED) {
            throw new IllegalStateException("Chỉ được xóa đọc giả đã hết hạn thẻ.");
        }
        memberDAO.softDelete(id);
    }

    /**
     * Gia hạn thẻ có phí 100.000đ, thêm {@value #MEMBERSHIP_EXTENSION_YEARS} năm từ hôm nay.
     * Chỉ áp dụng khi trạng thái EXPIRED (UI).
     */
    public Fine renewExpiredCardWithFee(int memberId) {
        Member member = memberDAO.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Đọc giả không tồn tại."));
        if (member.getStatus() != Member.Status.EXPIRED) {
            throw new IllegalStateException("Chỉ đọc giả hết hạn thẻ mới cần gia hạn có phí.");
        }
        Fine fine = Fine.forMember(memberId, RENEW_CARD_FEE,
            "Gia hạn thẻ đọc " + MEMBERSHIP_EXTENSION_YEARS + " năm (" + RENEW_CARD_FEE.toPlainString() + "đ)");
        fineDAO.save(fine);
        member.setExpiryDate(LocalDate.now().plusYears(MEMBERSHIP_EXTENSION_YEARS));
        member.setStatus(Member.Status.ACTIVE);
        memberDAO.update(member);
        return fine;
    }

    /**
     * Mở khóa thẻ SUSPENDED sau khi thu 50.000đ; reset bộ đếm mất sách.
     */
    public Fine unlockSuspendedCardWithFee(int memberId) {
        Member member = memberDAO.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Đọc giả không tồn tại."));
        if (member.getStatus() != Member.Status.SUSPENDED) {
            throw new IllegalStateException("Đọc giả không đang bị khóa thẻ.");
        }
        Fine fine = Fine.forMember(memberId, UNLOCK_CARD_FEE,
            "Mở khóa thẻ đọc (" + UNLOCK_CARD_FEE.toPlainString() + "đ)");
        fineDAO.save(fine);
        memberDAO.unlockSuspendedCard(memberId);
        return fine;
    }

    /** Gia hạn nội bộ (không thu phí) — dùng test / tạm. */
    public void renewCard(int memberId, int years) {
        Member member = memberDAO.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Đọc giả không tồn tại."));
        LocalDate newExpiry = member.getExpiryDate().isAfter(LocalDate.now())
            ? member.getExpiryDate().plusYears(years)
            : LocalDate.now().plusYears(years);
        member.setExpiryDate(newExpiry);
        member.setStatus(Member.Status.ACTIVE);
        memberDAO.update(member);
    }

    public long getTotalMembers() {
        return memberDAO.countTotal();
    }

    public void syncExpiredStatuses() {
        List<Member> all = memberDAO.findAll();
        for (Member m : all) {
            if (m.getStatus() == Member.Status.ACTIVE
                    && m.getExpiryDate() != null
                    && LocalDate.now().isAfter(m.getExpiryDate())) {
                m.setStatus(Member.Status.EXPIRED);
                memberDAO.update(m);
            }
        }
    }

    private void validateMember(Member member) {
        if (member.getFullName() == null || member.getFullName().isBlank())
            throw new IllegalArgumentException("Họ tên đọc giả không được để trống.");
    }
}
