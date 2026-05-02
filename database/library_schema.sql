-- ============================================================
-- DATABASE
-- ============================================================
CREATE DATABASE IF NOT EXISTS library_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE library_db;

-- ============================================================
-- USERS (ADMIN / LIBRARIAN)
-- ============================================================
CREATE TABLE users (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name     VARCHAR(100) NOT NULL,
  email         VARCHAR(100),
  role          ENUM('ADMIN', 'LIBRARIAN') NOT NULL DEFAULT 'LIBRARIAN',
  active        BOOLEAN NOT NULL DEFAULT TRUE,

  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY uq_users_username (username),
  UNIQUE KEY uq_users_email (email)
);

-- ============================================================
-- CATEGORIES
-- ============================================================
CREATE TABLE categories (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  description TEXT,

  deleted_at  TIMESTAMP NULL,

  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY uq_categories_name (name, deleted_at)
);

CREATE INDEX idx_categories_not_deleted ON categories(deleted_at);

-- ============================================================
-- BOOKS
-- ============================================================
CREATE TABLE books (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  isbn             VARCHAR(20),
  title            VARCHAR(255) NOT NULL,
  author           VARCHAR(255) NOT NULL,
  publisher        VARCHAR(255),
  publish_year     INT,
  category_id      INT,

  total_copies     INT NOT NULL DEFAULT 1,

  description      TEXT,
  cover_image_path VARCHAR(500),

  deleted_at       TIMESTAMP NULL,

  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_books_category
    FOREIGN KEY (category_id)
    REFERENCES categories(id)
    ON DELETE SET NULL,

  CONSTRAINT chk_books_total CHECK (total_copies >= 0),

  UNIQUE KEY uq_books_isbn (isbn, deleted_at)
);

CREATE INDEX idx_books_title        ON books(title);
CREATE INDEX idx_books_author       ON books(author);
CREATE INDEX idx_books_category     ON books(category_id);
CREATE INDEX idx_books_not_deleted  ON books(deleted_at);

-- ============================================================
-- MEMBERS (READERS)
-- ============================================================
CREATE TABLE members (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  member_code VARCHAR(20)  NOT NULL,
  full_name   VARCHAR(100) NOT NULL,
  email       VARCHAR(100),
  phone       VARCHAR(20),
  address     TEXT,

  join_date   DATE NOT NULL DEFAULT (CURRENT_DATE),
  expiry_date DATE NOT NULL,

  status      ENUM('ACTIVE', 'EXPIRED', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',

  lost_book_count INT NOT NULL DEFAULT 0 COMMENT 'Tăng khi phiếu mượn chuyển LOST; reset khi mở khóa thẻ',

  deleted_at  TIMESTAMP NULL,

  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY uq_members_code  (member_code, deleted_at),
  UNIQUE KEY uq_members_email (email, deleted_at)
);

CREATE INDEX idx_members_name        ON members(full_name);
CREATE INDEX idx_members_not_deleted ON members(deleted_at);

-- ============================================================
-- BORROW RECORDS (HISTORICAL - NO SOFT DELETE)
-- ============================================================
CREATE TABLE borrow_records (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  member_id   INT NOT NULL,
  book_id     INT NOT NULL,

  borrow_date DATE NOT NULL DEFAULT (CURRENT_DATE),
  due_date    DATE NOT NULL,
  return_date DATE,

  status      ENUM('BORROWING', 'RETURNED', 'OVERDUE', 'LOST')
                NOT NULL DEFAULT 'BORROWING',

  notes       TEXT,

  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_borrow_member
    FOREIGN KEY (member_id)
    REFERENCES members(id)
    ON DELETE RESTRICT,

  CONSTRAINT fk_borrow_book
    FOREIGN KEY (book_id)
    REFERENCES books(id)
    ON DELETE RESTRICT
);

CREATE INDEX idx_borrow_member ON borrow_records(member_id);
CREATE INDEX idx_borrow_book   ON borrow_records(book_id);
CREATE INDEX idx_borrow_status ON borrow_records(status);

-- ============================================================
-- FINES (HISTORICAL)
-- ============================================================
CREATE TABLE fines (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  borrow_record_id  INT NULL,
  member_id         INT NULL,

  amount            DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
  reason            VARCHAR(255),

  paid              BOOLEAN NOT NULL DEFAULT FALSE,
  paid_date         DATE,

  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_fine_borrow
    FOREIGN KEY (borrow_record_id)
    REFERENCES borrow_records(id)
    ON DELETE CASCADE,

  CONSTRAINT fk_fine_member
    FOREIGN KEY (member_id)
    REFERENCES members(id)
    ON DELETE CASCADE,

  CONSTRAINT chk_fine_has_ref
    CHECK (borrow_record_id IS NOT NULL OR member_id IS NOT NULL)
);

CREATE INDEX idx_fines_paid ON fines(paid);
