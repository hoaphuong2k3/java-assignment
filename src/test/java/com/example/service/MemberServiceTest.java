package com.example.service;

import com.example.dao.MemberDAO;
import com.example.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberDAO memberDAO;

    @InjectMocks
    private MemberService memberService;

    private Member existingMember;

    @BeforeEach
    void setUp() {
        existingMember = new Member();
        existingMember.setId(1);
        existingMember.setMemberCode("M001");
        existingMember.setFullName("Tran Thi B");
        existingMember.setEmail("b@test.com");
        existingMember.setPhone("0901000001");
        existingMember.setJoinDate(LocalDate.now().minusYears(1));
        existingMember.setExpiryDate(LocalDate.now().plusYears(1));
        existingMember.setStatus(Member.Status.ACTIVE);
    }

    // ---- getAllMembers ----

    @Test
    void getAllMembers_delegatesToDao() {
        when(memberDAO.findAll()).thenReturn(List.of(existingMember));
        List<Member> result = memberService.getAllMembers();
        assertEquals(1, result.size());
        verify(memberDAO).findAll();
    }

    // ---- searchMembers ----

    @Test
    void searchMembers_blankKeyword_returnsAll() {
        when(memberDAO.findAll()).thenReturn(List.of(existingMember));
        List<Member> result = memberService.searchMembers("  ");
        assertEquals(1, result.size());
        verify(memberDAO).findAll();
        verify(memberDAO, never()).search(any());
    }

    @Test
    void searchMembers_withKeyword_delegatesToDaoSearch() {
        when(memberDAO.search("tran")).thenReturn(List.of(existingMember));
        List<Member> result = memberService.searchMembers("tran");
        assertEquals(1, result.size());
        verify(memberDAO).search("tran");
    }

    @Test
    void searchMembers_nullKeyword_returnsAll() {
        when(memberDAO.findAll()).thenReturn(List.of());
        memberService.searchMembers(null);
        verify(memberDAO).findAll();
    }

    // ---- addMember ----

    @Test
    void addMember_validMember_setsCodeAndSaves() {
        when(memberDAO.generateNextMemberCode()).thenReturn("M002");

        Member newMember = new Member();
        newMember.setFullName("Le Van C");
        newMember.setExpiryDate(LocalDate.now().plusYears(2));
        newMember.setStatus(Member.Status.ACTIVE);

        memberService.addMember(newMember);

        assertEquals("M002", newMember.getMemberCode());
        assertEquals(Member.Status.ACTIVE, newMember.getStatus());
        assertNotNull(newMember.getJoinDate());
        assertEquals(LocalDate.now().plusYears(MemberService.MEMBERSHIP_EXTENSION_YEARS), newMember.getExpiryDate());
        verify(memberDAO).save(newMember);
    }

    @Test
    void addMember_setsExpiryDateIfNull() {
        when(memberDAO.generateNextMemberCode()).thenReturn("M003");

        Member newMember = new Member();
        newMember.setFullName("Le Van C");
        // expiryDate left null intentionally

        memberService.addMember(newMember);

        assertEquals(LocalDate.now().plusYears(MemberService.MEMBERSHIP_EXTENSION_YEARS), newMember.getExpiryDate());
        verify(memberDAO).save(newMember);
    }

    @Test
    void addMember_emptyName_throwsIllegalArgument() {
        Member bad = new Member();
        bad.setFullName("  ");

        assertThrows(IllegalArgumentException.class, () -> memberService.addMember(bad));
        verify(memberDAO, never()).save(any());
    }

    @Test
    void addMember_nullName_throwsIllegalArgument() {
        Member bad = new Member();
        bad.setFullName(null);

        assertThrows(IllegalArgumentException.class, () -> memberService.addMember(bad));
    }

    // ---- updateMember ----

    @Test
    void updateMember_existingMember_savesUpdate() {
        when(memberDAO.findById(1)).thenReturn(Optional.of(existingMember));

        memberService.updateMember(existingMember);

        verify(memberDAO).updatePersonalInfo(eq(1), eq("Tran Thi B"), eq("b@test.com"),
            eq("0901000001"), anyString());
    }

    @Test
    void updateMember_nonExistentId_throwsIllegalArgument() {
        when(memberDAO.findById(99)).thenReturn(Optional.empty());

        Member ghost = new Member();
        ghost.setId(99);
        ghost.setFullName("Ghost");

        assertThrows(IllegalArgumentException.class, () -> memberService.updateMember(ghost));
        verify(memberDAO, never()).updatePersonalInfo(anyInt(), any(), any(), any(), any());
    }

    // ---- deleteMember ----

    @Test
    void deleteMember_expired_callsSoftDelete() {
        existingMember.setStatus(Member.Status.EXPIRED);
        when(memberDAO.findById(1)).thenReturn(Optional.of(existingMember));

        memberService.deleteMember(1);

        verify(memberDAO).softDelete(1);
    }

    @Test
    void deleteMember_active_throwsIllegalState() {
        when(memberDAO.findById(1)).thenReturn(Optional.of(existingMember));

        assertThrows(IllegalStateException.class, () -> memberService.deleteMember(1));
        verify(memberDAO, never()).softDelete(anyInt());
    }

    @Test
    void deleteMember_nonExistent_throwsIllegalArgument() {
        when(memberDAO.findById(42)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> memberService.deleteMember(42));
        verify(memberDAO, never()).softDelete(anyInt());
    }

    // ---- renewCard ----

    @Test
    void renewCard_expiredMember_setsNewExpiryFromToday() {
        existingMember.setExpiryDate(LocalDate.now().minusDays(30)); // already expired
        existingMember.setStatus(Member.Status.EXPIRED);
        when(memberDAO.findById(1)).thenReturn(Optional.of(existingMember));

        memberService.renewCard(1, 2);

        LocalDate expectedExpiry = LocalDate.now().plusYears(2);
        assertEquals(expectedExpiry, existingMember.getExpiryDate());
        assertEquals(Member.Status.ACTIVE, existingMember.getStatus());
        verify(memberDAO).update(existingMember);
    }

    @Test
    void renewCard_activeMember_extendsExpiryFromCurrentExpiry() {
        LocalDate currentExpiry = LocalDate.now().plusMonths(6);
        existingMember.setExpiryDate(currentExpiry);
        when(memberDAO.findById(1)).thenReturn(Optional.of(existingMember));

        memberService.renewCard(1, 2);

        assertEquals(currentExpiry.plusYears(2), existingMember.getExpiryDate());
        assertEquals(Member.Status.ACTIVE, existingMember.getStatus());
        verify(memberDAO).update(existingMember);
    }

    @Test
    void renewCard_nonExistent_throwsIllegalArgument() {
        when(memberDAO.findById(77)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> memberService.renewCard(77, 1));
        verify(memberDAO, never()).update(any());
    }

    // ---- syncExpiredStatuses ----

    @Test
    void syncExpiredStatuses_expiredActiveMember_updatesStatusToExpired() {
        Member expiredMember = new Member();
        expiredMember.setId(5);
        expiredMember.setFullName("Expired User");
        expiredMember.setStatus(Member.Status.ACTIVE);
        expiredMember.setExpiryDate(LocalDate.now().minusDays(1));

        when(memberDAO.findAll()).thenReturn(List.of(expiredMember));

        memberService.syncExpiredStatuses();

        assertEquals(Member.Status.EXPIRED, expiredMember.getStatus());
        verify(memberDAO).update(expiredMember);
    }

    @Test
    void syncExpiredStatuses_activeMemberNotExpiredYet_noUpdate() {
        existingMember.setExpiryDate(LocalDate.now().plusDays(1));
        when(memberDAO.findAll()).thenReturn(List.of(existingMember));

        memberService.syncExpiredStatuses();

        verify(memberDAO, never()).update(any());
    }

    @Test
    void syncExpiredStatuses_suspendedAndPastExpiry_updatesToExpired() {
        existingMember.setStatus(Member.Status.SUSPENDED);
        existingMember.setExpiryDate(LocalDate.now().minusDays(1));
        when(memberDAO.findAll()).thenReturn(List.of(existingMember));

        memberService.syncExpiredStatuses();

        assertEquals(Member.Status.EXPIRED, existingMember.getStatus());
        verify(memberDAO).update(existingMember);
    }

    @Test
    void syncExpiredStatuses_suspendedButCardNotExpiredYet_noUpdate() {
        existingMember.setStatus(Member.Status.SUSPENDED);
        existingMember.setExpiryDate(LocalDate.now().plusDays(30));
        when(memberDAO.findAll()).thenReturn(List.of(existingMember));

        memberService.syncExpiredStatuses();

        verify(memberDAO, never()).update(any());
        assertEquals(Member.Status.SUSPENDED, existingMember.getStatus());
    }
}
