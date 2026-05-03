package com.example.dao;

import com.example.config.DatabaseConfig;
import com.example.model.Fine;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FineDAO {

    private Connection getConn() {
        return DatabaseConfig.getInstance().getConnection();
    }

    /**
     * Phiếu mượn (có thể borrow_record_id NULL khi phí chỉ gắn member_id).
     */
    private static final String JOIN_SQL = """
        SELECT f.*,
               m.full_name   AS member_name,
               m.member_code AS member_code,
               COALESCE(b.title, '—') AS book_title,
               br.borrow_date, br.due_date, br.return_date
        FROM fines f
        LEFT JOIN borrow_records br ON f.borrow_record_id = br.id
        JOIN members m ON m.id = COALESCE(f.member_id, br.member_id)
        LEFT JOIN books b ON br.book_id = b.id
        """;

    private Fine mapRow(ResultSet rs) throws SQLException {
        Fine f = new Fine();
        f.setId(rs.getInt("id"));
        int brid = rs.getInt("borrow_record_id");
        f.setBorrowRecordId(rs.wasNull() ? null : brid);
        int mid = rs.getInt("member_id");
        f.setMemberId(rs.wasNull() ? null : mid);
        f.setAmount(rs.getBigDecimal("amount"));
        f.setReason(rs.getString("reason"));
        f.setPaid(rs.getBoolean("paid"));
        Date pd = rs.getDate("paid_date");
        if (pd != null) f.setPaidDate(pd.toLocalDate());
        try { f.setMemberName(rs.getString("member_name")); } catch (SQLException ignored) {}
        try { f.setMemberCode(rs.getString("member_code")); } catch (SQLException ignored) {}
        try { f.setBookTitle(rs.getString("book_title")); } catch (SQLException ignored) {}
        try {
            Date bd = rs.getDate("borrow_date");
            if (bd != null) f.setBorrowDate(bd.toLocalDate());
            Date dd = rs.getDate("due_date");
            if (dd != null) f.setDueDate(dd.toLocalDate());
            Date rd = rs.getDate("return_date");
            if (rd != null) f.setReturnDate(rd.toLocalDate());
        } catch (SQLException ignored) {}
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) f.setCreatedAt(ts.toLocalDateTime());
        ts = rs.getTimestamp("updated_at");
        if (ts != null) f.setUpdatedAt(ts.toLocalDateTime());
        return f;
    }

    public List<Fine> findAll() {
        List<Fine> list = new ArrayList<>();
        String sql = JOIN_SQL + " ORDER BY f.created_at DESC";
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching fines", e);
        }
        return list;
    }

    public List<Fine> findUnpaid() {
        List<Fine> list = new ArrayList<>();
        String sql = JOIN_SQL + " WHERE f.paid = FALSE ORDER BY f.created_at DESC";
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching unpaid fines", e);
        }
        return list;
    }

    public Optional<Fine> findByBorrowRecordId(int borrowRecordId) {
        String sql = JOIN_SQL + " WHERE f.borrow_record_id = ?";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, borrowRecordId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding fine by borrow record", e);
        }
        return Optional.empty();
    }

    public List<Fine> search(String keyword) {
        List<Fine> list = new ArrayList<>();
        String sql = JOIN_SQL + " WHERE m.full_name LIKE ? OR m.member_code LIKE ? OR b.title LIKE ? OR f.reason LIKE ? ORDER BY f.created_at DESC";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            String kw = "%" + keyword + "%";
            ps.setString(1, kw); ps.setString(2, kw); ps.setString(3, kw); ps.setString(4, kw);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error searching fines", e);
        }
        return list;
    }

    public void save(Fine fine) {
        String sql = "INSERT INTO fines (borrow_record_id, member_id, amount, reason, paid) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = getConn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (fine.getBorrowRecordId() != null) ps.setInt(1, fine.getBorrowRecordId());
            else ps.setNull(1, Types.INTEGER);
            if (fine.getMemberId() != null) ps.setInt(2, fine.getMemberId());
            else ps.setNull(2, Types.INTEGER);
            ps.setBigDecimal(3, fine.getAmount());
            ps.setString(4, fine.getReason());
            ps.setBoolean(5, fine.isPaid());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) fine.setId(keys.getInt(1));
        } catch (SQLException e) {
            throw new RuntimeException("Error saving fine", e);
        }
    }

    public void markAsPaid(int fineId) {
        String sql = "UPDATE fines SET paid=TRUE, paid_date=CURDATE() WHERE id=?";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, fineId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error marking fine as paid", e);
        }
    }

    public void update(Fine fine) {
        String sql = "UPDATE fines SET amount=?, reason=?, paid=?, paid_date=? WHERE id=?";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setBigDecimal(1, fine.getAmount());
            ps.setString(2, fine.getReason());
            ps.setBoolean(3, fine.isPaid());
            if (fine.getPaidDate() != null) ps.setDate(4, Date.valueOf(fine.getPaidDate()));
            else ps.setNull(4, Types.DATE);
            ps.setInt(5, fine.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating fine", e);
        }
    }

    public List<Fine> findByDateRange(java.time.LocalDate from, java.time.LocalDate to) {
        List<Fine> list = new ArrayList<>();
        String sql = JOIN_SQL + " WHERE DATE(f.created_at) BETWEEN ? AND ? ORDER BY f.created_at DESC";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching fines by date range", e);
        }
        return list;
    }

    public BigDecimal totalUnpaidAmount() {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM fines WHERE paid = FALSE";
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getBigDecimal(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error summing unpaid fines", e);
        }
        return BigDecimal.ZERO;
    }
}
