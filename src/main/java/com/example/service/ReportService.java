package com.example.service;

import com.example.config.DatabaseConfig;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportService {

    private Connection getConn() {
        return DatabaseConfig.getInstance().getConnection();
    }

    /** Dashboard summary statistics */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            stats.put("totalBooks",       queryLong("SELECT COUNT(*) FROM books WHERE deleted_at IS NULL"));
            stats.put("totalMembers",     queryLong("SELECT COUNT(*) FROM members WHERE status='ACTIVE' AND deleted_at IS NULL"));
            stats.put("activeBorrows",    queryLong("SELECT COUNT(*) FROM borrow_records WHERE status IN ('BORROWING','OVERDUE')"));
            stats.put("overdueCount",     queryLong("SELECT COUNT(*) FROM borrow_records WHERE status='OVERDUE' OR (status='BORROWING' AND due_date < CURDATE())"));
            stats.put("unpaidFines",      queryDecimal("SELECT COALESCE(SUM(amount),0) FROM fines WHERE paid=FALSE"));
            stats.put("borrowsThisMonth", queryLong("SELECT COUNT(*) FROM borrow_records WHERE MONTH(borrow_date)=MONTH(CURDATE()) AND YEAR(borrow_date)=YEAR(CURDATE())"));
        } catch (Exception e) {
            throw new RuntimeException("Error loading dashboard stats", e);
        }
        return stats;
    }

    /** Top 10 most borrowed books */
    public List<Map<String, Object>> getTopBorrowedBooks(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = """
            SELECT b.title, b.author, COUNT(*) AS borrow_count
            FROM borrow_records br JOIN books b ON br.book_id = b.id
            GROUP BY b.id, b.title, b.author
            ORDER BY borrow_count DESC
            LIMIT ?
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("title", rs.getString("title"));
                row.put("author", rs.getString("author"));
                row.put("borrowCount", rs.getLong("borrow_count"));
                result.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading top borrowed books", e);
        }
        return result;
    }

    /** Most active members (by borrow count) */
    public List<Map<String, Object>> getTopActiveMembers(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = """
            SELECT m.member_code, m.full_name, COUNT(*) AS borrow_count
            FROM borrow_records br JOIN members m ON br.member_id = m.id
            GROUP BY m.id, m.member_code, m.full_name
            ORDER BY borrow_count DESC
            LIMIT ?
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("memberCode", rs.getString("member_code"));
                row.put("fullName",   rs.getString("full_name"));
                row.put("borrowCount", rs.getLong("borrow_count"));
                result.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading top active members", e);
        }
        return result;
    }

    /** Monthly borrow statistics for the current year */
    public List<Map<String, Object>> getMonthlyBorrowStats(int year) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = """
            SELECT MONTH(borrow_date) AS month, COUNT(*) AS total
            FROM borrow_records
            WHERE YEAR(borrow_date) = ?
            GROUP BY MONTH(borrow_date)
            ORDER BY month
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, year);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("month", rs.getInt("month"));
                row.put("total", rs.getLong("total"));
                result.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading monthly borrow stats", e);
        }
        return result;
    }

    /** Books count per category */
    public List<Map<String, Object>> getBooksPerCategory() {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = """
            SELECT c.name AS category, COUNT(b.id) AS book_count
            FROM categories c
            LEFT JOIN books b ON b.category_id = c.id AND b.deleted_at IS NULL
            WHERE c.deleted_at IS NULL
            GROUP BY c.id, c.name
            ORDER BY book_count DESC
            """;
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category",  rs.getString("category"));
                row.put("bookCount", rs.getLong("book_count"));
                result.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading books per category", e);
        }
        return result;
    }

    /**
     * Tổng phí gia hạn thẻ đọc (ghi trong fines, lý do chứa "Gia hạn thẻ đọc") trong năm lịch.
     */
    public Map<String, Object> getCardRenewalFineSummary(int year) {
        Map<String, Object> map = new LinkedHashMap<>();
        String sql = """
            SELECT
              COUNT(*) AS cnt,
              COALESCE(SUM(amount), 0) AS total,
              COALESCE(SUM(CASE WHEN paid THEN amount ELSE 0 END), 0) AS paid_sum,
              COALESCE(SUM(CASE WHEN NOT paid THEN amount ELSE 0 END), 0) AS unpaid_sum
            FROM fines
            WHERE YEAR(created_at) = ?
              AND reason LIKE '%Gia hạn thẻ đọc%'
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, year);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                map.put("count", rs.getLong("cnt"));
                map.put("total", rs.getBigDecimal("total"));
                map.put("paidSum", rs.getBigDecimal("paid_sum"));
                map.put("unpaidSum", rs.getBigDecimal("unpaid_sum"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading renewal fee stats", e);
        }
        if (map.isEmpty()) {
            map.put("count", 0L);
            map.put("total", BigDecimal.ZERO);
            map.put("paidSum", BigDecimal.ZERO);
            map.put("unpaidSum", BigDecimal.ZERO);
        }
        return map;
    }

    /** Đếm đọc giả đăng ký (join_date) trong khoảng đóng; nếu from > to thì đổi chỗ. */
    public long countMembersJoinedBetween(java.time.LocalDate from, java.time.LocalDate to) {
        if (from == null || to == null) return 0;
        java.time.LocalDate a = from;
        java.time.LocalDate b = to;
        if (a.isAfter(b)) {
            java.time.LocalDate t = a;
            a = b;
            b = t;
        }
        String sql = """
            SELECT COUNT(*) FROM members
            WHERE deleted_at IS NULL AND join_date BETWEEN ? AND ?
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(a));
            ps.setDate(2, Date.valueOf(b));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting members by join date", e);
        }
        return 0;
    }

    private long queryLong(String sql) throws SQLException {
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getLong(1);
        }
        return 0;
    }

    private BigDecimal queryDecimal(String sql) throws SQLException {
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getBigDecimal(1);
        }
        return BigDecimal.ZERO;
    }
}
