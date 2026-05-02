package com.example.dao;

import com.example.config.DatabaseConfig;
import com.example.model.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CategoryDAO {

    private Connection getConn() {
        return DatabaseConfig.getInstance().getConnection();
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        Category c = new Category();
        c.setId(rs.getInt("id"));
        c.setName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) c.setCreatedAt(ts.toLocalDateTime());
        return c;
    }

    public List<Category> findAll() {
        List<Category> list = new ArrayList<>();
        String sql = "SELECT * FROM categories WHERE deleted_at IS NULL ORDER BY name";
        try (Statement st = getConn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching categories", e);
        }
        return list;
    }

    public Optional<Category> findById(int id) {
        String sql = "SELECT * FROM categories WHERE id = ? AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding category", e);
        }
        return Optional.empty();
    }

    public List<Category> search(String keyword) {
        List<Category> list = new ArrayList<>();
        String sql = "SELECT * FROM categories WHERE deleted_at IS NULL AND name LIKE ? ORDER BY name";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error searching categories", e);
        }
        return list;
    }

    public void save(Category category) {
        String sql = "INSERT INTO categories (name, description) VALUES (?, ?)";
        try (PreparedStatement ps = getConn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) category.setId(keys.getInt(1));
        } catch (SQLException e) {
            throw new RuntimeException("Error saving category", e);
        }
    }

    public void update(Category category) {
        String sql = "UPDATE categories SET name=?, description=? WHERE id=?";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.setInt(3, category.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating category", e);
        }
    }

    public void delete(int id) {
        String sql = "UPDATE categories SET deleted_at = CURRENT_TIMESTAMP WHERE id=? AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting category", e);
        }
    }

    /** Trùng tên (không phân biệt hoa thường), bỏ qua id khi sửa. */
    public boolean existsNameForOtherCategory(String name, Integer excludeCategoryId) {
        if (name == null || name.isBlank()) return false;
        String norm = name.trim();
        String sql = """
            SELECT COUNT(*) FROM categories
            WHERE deleted_at IS NULL AND LOWER(TRIM(name)) = LOWER(?)
            AND (? IS NULL OR id <> ?)
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setString(1, norm);
            if (excludeCategoryId == null) {
                ps.setNull(2, Types.INTEGER);
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(2, excludeCategoryId);
                ps.setInt(3, excludeCategoryId);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error checking category name uniqueness", e);
        }
        return false;
    }

    /** Phiếu BORROWING/OVERDUE của sách thuộc danh mục (chặn xóa danh mục/sách đang mượn). */
    public int countBorrowingOrOverdueInCategory(int categoryId) {
        String sql = """
            SELECT COUNT(*) FROM borrow_records br
            INNER JOIN books b ON br.book_id = b.id
            WHERE b.category_id = ? AND b.deleted_at IS NULL
              AND br.status IN ('BORROWING','OVERDUE')
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting active borrows in category", e);
        }
        return 0;
    }

    public long countBooksInCategory(int categoryId) {
        String sql = "SELECT COUNT(*) FROM books WHERE category_id=? AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting books in category", e);
        }
        return 0;
    }

    /** Soft-delete all books belonging to a category, then delete the category itself. */
    public void deleteWithBooks(int categoryId) {
        String sqlBooks = "UPDATE books SET deleted_at = CURRENT_TIMESTAMP WHERE category_id=? AND deleted_at IS NULL";
        String sqlCat   = "UPDATE categories SET deleted_at = CURRENT_TIMESTAMP WHERE id=? AND deleted_at IS NULL";
        try (PreparedStatement ps1 = getConn().prepareStatement(sqlBooks);
             PreparedStatement ps2 = getConn().prepareStatement(sqlCat)) {
            ps1.setInt(1, categoryId); ps1.executeUpdate();
            ps2.setInt(1, categoryId); ps2.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting category with books", e);
        }
    }

    public List<com.example.model.Book> findBooksByCategory(int categoryId) {
        List<com.example.model.Book> list = new ArrayList<>();
        String sql = """
            SELECT b.id, b.title, b.author, b.isbn, b.total_copies,
              (b.total_copies - COALESCE((
                SELECT COUNT(*) FROM borrow_records br
                WHERE br.book_id = b.id AND br.status IN ('BORROWING','OVERDUE','LOST')
              ), 0)) AS available_copies
            FROM books b
            WHERE b.category_id=? AND b.deleted_at IS NULL ORDER BY b.title
            """;
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                com.example.model.Book b = new com.example.model.Book();
                b.setId(rs.getInt("id"));
                b.setTitle(rs.getString("title"));
                b.setAuthor(rs.getString("author"));
                b.setIsbn(rs.getString("isbn"));
                b.setTotalCopies(rs.getInt("total_copies"));
                b.setAvailableCopies(rs.getInt("available_copies"));
                list.add(b);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching books by category", e);
        }
        return list;
    }

    public void deleteMultiple(List<Integer> ids) {
        if (ids.isEmpty()) return;
        String sql = "UPDATE categories SET deleted_at = CURRENT_TIMESTAMP WHERE id=? AND deleted_at IS NULL";
        try (PreparedStatement ps = getConn().prepareStatement(sql)) {
            for (int id : ids) {
                ps.setInt(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting categories", e);
        }
    }
}
