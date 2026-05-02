package com.example.dao;

import com.example.config.DatabaseConfig;
import com.example.model.BorrowRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BorrowRecordDAO {

    private Connection getConn() {
        return DatabaseConfig.getInstance().getConnection();
    }

    private BorrowRecord mapRow(ResultSet rs) throws SQLException {
        BorrowRecord br = new BorrowRecord();
        br.setId(rs.getInt("id"));
        br.setMemberId(rs.getInt("member_id"));
        br.setBookId(rs.getInt("book_id"));
        Date bd = rs.getDate("borrow_date");
        if (bd != null) br.setBorrowDate(bd.toLocalDate());
        Date dd = rs.getDate("due_date");
        if (dd != null) br.setDueDate(dd.toLocalDate());
        Date rd = rs.getDate("return_date");
        if (rd != null) br.setReturnDate(rd.toLocalDate());
        br.setStatus(BorrowRecord.Status.valueOf(rs.getString("status")));
        br.setNotes(rs.getString("notes"));
        try { br.setMemberName(rs.getString("member_name")); } catch (SQLException ignored) {}
        try { br.setMemberCode(rs.getString("member_code")); } catch (SQLException ignored) {}
        try { br.setBookTitle(rs.getString("book_title")); } catch (SQLException ignored) {}
        try { br.setBookAuthor(rs.getString("book_author")); } catch (SQLException ignored) {}
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) br.setCreatedAt(ts.toLocalDateTime());
        return br;
    }

    private static final String JOIN_SQL = """
        SELECT br.*,
               m.full_name  AS member_name,
               m.member_code AS member_code,
               b.title      AS book_title,
               b.author     AS book_author
        FROM borrow_records br
        JOIN members m ON br.member_id = m.id
        JOIN books   b ON br.book_id   = b.id
        """;

    public List<BorrowRecord> findAll() {
        List<BorrowRecord> list = new ArrayList<>();
        String sql = JOIN_SQL + " ORDER BY br.borrow_date DESC, br.id DESC";
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching borrow records", e);
        }
        return list;
    }

    public Optional<BorrowRecord> findById(int id) {
        String sql = JOIN_SQL + " WHERE br.id = ?";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding borrow record", e);
        }
        return Optional.empty();
    }

    public List<BorrowRecord> findByMember(int memberId) {
        List<BorrowRecord> list = new ArrayList<>();
        String sql = JOIN_SQL + " WHERE br.member_id = ? ORDER BY br.borrow_date DESC, br.id DESC";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding borrow records by member", e);
        }
        return list;
    }

    public List<BorrowRecord> findActiveBorrows() {
        List<BorrowRecord> list = new ArrayList<>();
        String sql = JOIN_SQL + " WHERE br.status IN ('BORROWING','OVERDUE') ORDER BY br.due_date";
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding active borrows", e);
        }
        return list;
    }

    public List<BorrowRecord> findOverdue() {
        List<BorrowRecord> list = new ArrayList<>();
        String sql = JOIN_SQL + " WHERE br.status = 'OVERDUE' OR (br.status = 'BORROWING' AND br.due_date < CURDATE()) ORDER BY br.due_date";
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding overdue records", e);
        }
        return list;
    }

    public List<BorrowRecord> search(String keyword) {
        List<BorrowRecord> list = new ArrayList<>();
        String sql = JOIN_SQL + " WHERE m.full_name LIKE ? OR m.member_code LIKE ? OR b.title LIKE ? ORDER BY br.borrow_date DESC, br.id DESC";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            String kw = "%" + keyword + "%";
            ps.setString(1, kw); ps.setString(2, kw); ps.setString(3, kw);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error searching borrow records", e);
        }
        return list;
    }

    public void save(BorrowRecord record) {
        String sql = "INSERT INTO borrow_records (member_id, book_id, borrow_date, due_date, status, notes) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = getConn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, record.getMemberId());
            ps.setInt(2, record.getBookId());
            ps.setDate(3, Date.valueOf(record.getBorrowDate()));
            ps.setDate(4, Date.valueOf(record.getDueDate()));
            ps.setString(5, record.getStatus().name());
            ps.setString(6, record.getNotes());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) record.setId(keys.getInt(1));
        } catch (SQLException e) {
            throw new RuntimeException("Error saving borrow record", e);
        }
    }

    public void update(BorrowRecord record) {
        String sql = "UPDATE borrow_records SET return_date=?, status=?, notes=? WHERE id=?";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            if (record.getReturnDate() != null)
                ps.setDate(1, Date.valueOf(record.getReturnDate()));
            else
                ps.setNull(1, Types.DATE);
            ps.setString(2, record.getStatus().name());
            ps.setString(3, record.getNotes());
            ps.setInt(4, record.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating borrow record", e);
        }
    }

    /** Mark all BORROWING records past due_date as OVERDUE */
    public int markOverdueRecords() {
        String sql = "UPDATE borrow_records SET status='OVERDUE' WHERE status='BORROWING' AND due_date < CURDATE()";
        try (Statement st = getConn().createStatement()) {
            return st.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Error marking overdue records", e);
        }
    }

    public long countActiveBorrows() {
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM borrow_records WHERE status IN ('BORROWING','OVERDUE')")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting active borrows", e);
        }
        return 0;
    }

    public long countOverdue() {
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM borrow_records WHERE status='OVERDUE' OR (status='BORROWING' AND due_date < CURDATE())")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting overdue", e);
        }
        return 0;
    }

    public List<BorrowRecord> findByDateRange(java.time.LocalDate from, java.time.LocalDate to) {
        List<BorrowRecord> list = new ArrayList<>();
        String sql = JOIN_SQL + " WHERE br.borrow_date BETWEEN ? AND ? ORDER BY br.borrow_date DESC, br.id DESC";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching borrow records by date range", e);
        }
        return list;
    }

    public List<BorrowRecord> searchWithDateRange(String keyword, java.time.LocalDate from, java.time.LocalDate to) {
        List<BorrowRecord> list = new ArrayList<>();
        String sql = JOIN_SQL + " WHERE (m.full_name LIKE ? OR m.member_code LIKE ? OR b.title LIKE ?)" +
                     " AND br.borrow_date BETWEEN ? AND ? ORDER BY br.borrow_date DESC, br.id DESC";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            String kw = "%" + keyword + "%";
            ps.setString(1, kw); ps.setString(2, kw); ps.setString(3, kw);
            ps.setDate(4, Date.valueOf(from));
            ps.setDate(5, Date.valueOf(to));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error searching borrow records with date range", e);
        }
        return list;
    }

    /** Gia hạn mượn: chỉ khi đang BORROWING. */
    public void updateDueDate(int id, java.time.LocalDate newDueDate) {
        String sql = "UPDATE borrow_records SET due_date=? WHERE id=? AND status='BORROWING'";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(newDueDate));
            ps.setInt(2, id);
            int n = ps.executeUpdate();
            if (n == 0) throw new SQLException("No BORROWING record with id " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Error updating due date", e);
        }
    }

    /** Quá hạn (OVERDUE) và đã trễ hơn {@code minDaysPastDue} ngày kể từ hạn trả. */
    public List<BorrowRecord> findOverdueBeyondDays(int minDaysPastDue) {
        List<BorrowRecord> list = new ArrayList<>();
        String sql = JOIN_SQL + " WHERE br.status='OVERDUE' AND DATEDIFF(CURDATE(), br.due_date) > ? ORDER BY br.due_date";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, minDaysPastDue);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding long overdue records", e);
        }
        return list;
    }
}
