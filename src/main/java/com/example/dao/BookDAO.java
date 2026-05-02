package com.example.dao;

import com.example.config.DatabaseConfig;
import com.example.model.Book;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BookDAO {

    private static final String SELECT_BOOKS_BASE = """
        SELECT b.*, c.name AS category_name,
          (b.total_copies - COALESCE((
            SELECT COUNT(*) FROM borrow_records br
            WHERE br.book_id = b.id AND br.status IN ('BORROWING','OVERDUE','LOST')
          ), 0)) AS available_copies
        FROM books b
        LEFT JOIN categories c ON b.category_id = c.id
        WHERE b.deleted_at IS NULL
        """;

    private Connection getConn() {
        return DatabaseConfig.getInstance().getConnection();
    }

    private Book mapRow(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.setId(rs.getInt("id"));
        b.setIsbn(rs.getString("isbn"));
        b.setTitle(rs.getString("title"));
        b.setAuthor(rs.getString("author"));
        b.setPublisher(rs.getString("publisher"));
        int year = rs.getInt("publish_year");
        if (!rs.wasNull()) b.setPublishYear(year);
        else b.setPublishYear(null);
        int cat = rs.getInt("category_id");
        b.setCategoryId(rs.wasNull() ? null : cat);
        b.setTotalCopies(rs.getInt("total_copies"));
        b.setAvailableCopies(rs.getInt("available_copies"));
        b.setDescription(rs.getString("description"));
        b.setCoverImagePath(rs.getString("cover_image_path"));
        Timestamp del = rs.getTimestamp("deleted_at");
        if (del != null) b.setDeletedAt(del.toLocalDateTime());
        else b.setDeletedAt(null);
        try { b.setCategoryName(rs.getString("category_name")); } catch (SQLException ignored) {}
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) b.setCreatedAt(ts.toLocalDateTime());
        ts = rs.getTimestamp("updated_at");
        if (ts != null) b.setUpdatedAt(ts.toLocalDateTime());
        return b;
    }

    /**
     * Number of copies currently tied to open borrow rows (BORROWING, OVERDUE, LOST).
     */
    /**
     * Phiếu đang mượn hoặc quá hạn chưa trả (không gồm LOST — cho phép xóa sách mất).
     */
    public int countBorrowingOrOverdue(int bookId) {
        String sql = """
            SELECT COUNT(*) FROM borrow_records
            WHERE book_id = ? AND status IN ('BORROWING','OVERDUE')
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, bookId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting active borrows for book", e);
        }
        return 0;
    }

    public int countCopiesOut(int bookId) {
        String sql = """
            SELECT COUNT(*) FROM borrow_records
            WHERE book_id = ? AND status IN ('BORROWING','OVERDUE','LOST')
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, bookId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting copies out", e);
        }
        return 0;
    }

    public List<Book> findAll() {
        List<Book> list = new ArrayList<>();
        String sql = SELECT_BOOKS_BASE + " ORDER BY b.title";
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching books", e);
        }
        return list;
    }

    public Optional<Book> findById(int id) {
        String sql = SELECT_BOOKS_BASE + " AND b.id = ?";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding book by id", e);
        }
        return Optional.empty();
    }

    public List<Book> search(String keyword) {
        List<Book> list = new ArrayList<>();
        String sql = SELECT_BOOKS_BASE + """
             AND (b.title LIKE ? OR b.author LIKE ? OR b.isbn LIKE ?)
            ORDER BY b.title
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            String kw = "%" + keyword + "%";
            ps.setString(1, kw);
            ps.setString(2, kw);
            ps.setString(3, kw);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error searching books", e);
        }
        return list;
    }

    public List<Book> findByCategory(int categoryId) {
        List<Book> list = new ArrayList<>();
        String sql = SELECT_BOOKS_BASE + " AND b.category_id = ? ORDER BY b.title";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding books by category", e);
        }
        return list;
    }

    public void save(Book book) {
        String sql = """
            INSERT INTO books (isbn, title, author, publisher, publish_year,
                category_id, total_copies, description, cover_image_path)
            VALUES (?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, book.getIsbn());
            ps.setString(2, book.getTitle());
            ps.setString(3, book.getAuthor());
            ps.setString(4, book.getPublisher());
            if (book.getPublishYear() != null) ps.setInt(5, book.getPublishYear());
            else ps.setNull(5, Types.INTEGER);
            if (book.getCategoryId() != null) ps.setInt(6, book.getCategoryId());
            else ps.setNull(6, Types.INTEGER);
            ps.setInt(7, book.getTotalCopies());
            ps.setString(8, book.getDescription());
            ps.setString(9, book.getCoverImagePath());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) book.setId(keys.getInt(1));
        } catch (SQLException e) {
            throw new RuntimeException("Error saving book", e);
        }
    }

    public void update(Book book) {
        String sql = """
            UPDATE books SET isbn=?, title=?, author=?, publisher=?, publish_year=?,
                category_id=?, total_copies=?, description=?, cover_image_path=?
            WHERE id=? AND deleted_at IS NULL
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, book.getIsbn());
            ps.setString(2, book.getTitle());
            ps.setString(3, book.getAuthor());
            ps.setString(4, book.getPublisher());
            if (book.getPublishYear() != null) ps.setInt(5, book.getPublishYear());
            else ps.setNull(5, Types.INTEGER);
            if (book.getCategoryId() != null) ps.setInt(6, book.getCategoryId());
            else ps.setNull(6, Types.INTEGER);
            ps.setInt(7, book.getTotalCopies());
            ps.setString(8, book.getDescription());
            ps.setString(9, book.getCoverImagePath());
            ps.setInt(10, book.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating book", e);
        }
    }

    public void softDelete(int id) {
        String sql = "UPDATE books SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error soft-deleting book", e);
        }
    }

    public void decreaseTotalCopies(int bookId) {
        String sql = "UPDATE books SET total_copies = total_copies - 1 WHERE id = ? AND total_copies > 0 AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, bookId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error decreasing total copies", e);
        }
    }

    /** Trùng ISBN (sách chưa xóa), bỏ qua id khi sửa. ISBN rỗng không kiểm tra. */
    public boolean existsIsbnForOtherBook(String isbn, Integer excludeBookId) {
        if (isbn == null || isbn.isBlank()) return false;
        String norm = isbn.trim();
        String sql = """
            SELECT COUNT(*) FROM books
            WHERE deleted_at IS NULL AND LOWER(TRIM(isbn)) = LOWER(?)
            AND (? IS NULL OR id <> ?)
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, norm);
            if (excludeBookId == null) {
                ps.setNull(2, Types.INTEGER);
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(2, excludeBookId);
                ps.setInt(3, excludeBookId);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error checking ISBN uniqueness", e);
        }
        return false;
    }

    public long countTotal() {
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM books WHERE deleted_at IS NULL")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting books", e);
        }
        return 0;
    }
}
