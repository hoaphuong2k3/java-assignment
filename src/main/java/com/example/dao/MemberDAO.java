package com.example.dao;

import com.example.config.DatabaseConfig;
import com.example.model.Member;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MemberDAO {

    private Connection getConn() {
        return DatabaseConfig.getInstance().getConnection();
    }

    private Member mapRow(ResultSet rs) throws SQLException {
        Member m = new Member();
        m.setId(rs.getInt("id"));
        m.setMemberCode(rs.getString("member_code"));
        m.setFullName(rs.getString("full_name"));
        m.setEmail(rs.getString("email"));
        m.setPhone(rs.getString("phone"));
        m.setAddress(rs.getString("address"));
        Date joinDate = rs.getDate("join_date");
        if (joinDate != null) m.setJoinDate(joinDate.toLocalDate());
        Date expiryDate = rs.getDate("expiry_date");
        if (expiryDate != null) m.setExpiryDate(expiryDate.toLocalDate());
        m.setStatus(Member.Status.valueOf(rs.getString("status")));
        try {
            m.setLostBookCount(rs.getInt("lost_book_count"));
        } catch (SQLException e) {
            m.setLostBookCount(0);
        }
        Timestamp del = rs.getTimestamp("deleted_at");
        if (del != null) m.setDeletedAt(del.toLocalDateTime());
        else m.setDeletedAt(null);
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) m.setCreatedAt(ts.toLocalDateTime());
        ts = rs.getTimestamp("updated_at");
        if (ts != null) m.setUpdatedAt(ts.toLocalDateTime());
        return m;
    }

    public List<Member> findAll() {
        List<Member> list = new ArrayList<>();
        String sql = "SELECT * FROM members WHERE deleted_at IS NULL ORDER BY full_name";
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching members", e);
        }
        return list;
    }

    public Optional<Member> findById(int id) {
        String sql = "SELECT * FROM members WHERE id = ? AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding member", e);
        }
        return Optional.empty();
    }

    public Optional<Member> findByCode(String code) {
        String sql = "SELECT * FROM members WHERE member_code = ? AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding member by code", e);
        }
        return Optional.empty();
    }

    public List<Member> search(String keyword) {
        List<Member> list = new ArrayList<>();
        String sql = """
            SELECT * FROM members WHERE deleted_at IS NULL
            AND (full_name LIKE ? OR member_code LIKE ? OR email LIKE ? OR phone LIKE ?)
            ORDER BY full_name
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            String kw = "%" + keyword + "%";
            ps.setString(1, kw); ps.setString(2, kw);
            ps.setString(3, kw); ps.setString(4, kw);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error searching members", e);
        }
        return list;
    }

    public void save(Member member) {
        String sql = "INSERT INTO members (member_code, full_name, email, phone, address, join_date, expiry_date, status) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = getConn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, member.getMemberCode());
            ps.setString(2, member.getFullName());
            ps.setString(3, member.getEmail());
            ps.setString(4, member.getPhone());
            ps.setString(5, member.getAddress());
            ps.setDate(6, Date.valueOf(member.getJoinDate()));
            ps.setDate(7, Date.valueOf(member.getExpiryDate()));
            ps.setString(8, member.getStatus().name());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) member.setId(keys.getInt(1));
        } catch (SQLException e) {
            throw new RuntimeException("Error saving member", e);
        }
    }

    public void update(Member member) {
        String sql = "UPDATE members SET full_name=?, email=?, phone=?, address=?, expiry_date=?, status=? WHERE id=? AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, member.getFullName());
            ps.setString(2, member.getEmail());
            ps.setString(3, member.getPhone());
            ps.setString(4, member.getAddress());
            ps.setDate(5, Date.valueOf(member.getExpiryDate()));
            ps.setString(6, member.getStatus().name());
            ps.setInt(7, member.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating member", e);
        }
    }

    /** Soft delete: ẩn đọc giả, giữ FK phiếu mượn. */
    public void softDelete(int id) {
        String sql = "UPDATE members SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error soft-deleting member", e);
        }
    }

    public long countTotal() {
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM members WHERE deleted_at IS NULL")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting members", e);
        }
        return 0;
    }

    public String generateNextMemberCode() {
        String sql = "SELECT member_code FROM members ORDER BY id DESC LIMIT 1";
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                String last = rs.getString("member_code");
                int num = Integer.parseInt(last.replaceAll("[^0-9]", "")) + 1;
                return String.format("TV%03d", num);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error generating member code", e);
        }
        return "TV001";
    }

    /** Chỉ cập nhật thông tin liên hệ (không đổi hạn thẻ / trạng thái qua màn hình sửa). */
    public void updatePersonalInfo(int id, String fullName, String email, String phone, String address) {
        String sql = "UPDATE members SET full_name=?, email=?, phone=?, address=? WHERE id=? AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, address);
            ps.setInt(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating member personal info", e);
        }
    }

    public void incrementLostBookCount(int memberId) {
        String sql = "UPDATE members SET lost_book_count = lost_book_count + 1 WHERE id = ? AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error incrementing lost book count", e);
        }
    }

    public void suspendIfLostThreshold(int memberId, int threshold) {
        String sql = "UPDATE members SET status = 'SUSPENDED' WHERE id = ? AND deleted_at IS NULL AND lost_book_count >= ? AND status <> 'SUSPENDED'";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.setInt(2, threshold);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error suspending member", e);
        }
    }

    /** Sau khi đóng phí mở khóa: ACTIVE và reset bộ đếm mất sách. */
    public void unlockSuspendedCard(int memberId) {
        String sql = "UPDATE members SET status = 'ACTIVE', lost_book_count = 0 WHERE id = ? AND status = 'SUSPENDED' AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, memberId);
            int n = ps.executeUpdate();
            if (n == 0)
                throw new RuntimeException("Không thể mở khóa: đọc giả không tồn tại hoặc không bị khóa thẻ.");
        } catch (SQLException e) {
            throw new RuntimeException("Error unlocking member card", e);
        }
    }
}
