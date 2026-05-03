-- Dữ liệu demo cho trang Báo cáo (biểu đồ T1–T12, top sách, top độc giả, phân bố danh mục).
-- Chạy sau library_schema.sql và library_seed_data.sql (hoặc DB đã có bảng + ít nhất vài category/member/book).
--
-- Năm biểu đồ trên UI lấy theo combo box trong app — mặc định có thể là năm hiện tại.
-- Script này dùng năm 2026 để khớp seed mẫu trong repo; đổi @report_year nếu cần.

USE library_db;

SET @report_year := 2026;

-- ---------------------------------------------------------------------------
-- Thêm vài sách (phân bố danh mục đẹp hơn — có thể chạy lại sẽ trùng title nếu đã insert trước đó)
-- ---------------------------------------------------------------------------
INSERT INTO books (isbn, title, author, publisher, publish_year, category_id, total_copies, description)
SELECT NULL, CONCAT('Demo VH ', n.n), CONCAT('Tác giả VH ', n.n), 'NXB Demo', 2020, c.id, 5, 'Fake — báo cáo'
FROM categories c
JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) n ON TRUE
WHERE c.name = 'Văn học' AND c.deleted_at IS NULL
LIMIT 4;

INSERT INTO books (isbn, title, author, publisher, publish_year, category_id, total_copies, description)
SELECT NULL, CONCAT('Demo KH ', n.n), CONCAT('Tác giả KH ', n.n), 'NXB Demo', 2021, c.id, 4, 'Fake — báo cáo'
FROM categories c
JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3) n ON TRUE
WHERE c.name = 'Khoa học' AND c.deleted_at IS NULL
LIMIT 3;

INSERT INTO books (isbn, title, author, publisher, publish_year, category_id, total_copies, description)
SELECT NULL, CONCAT('Demo LS ', n.n), CONCAT('Tác giả LS ', n.n), 'NXB Demo', 2022, c.id, 3, 'Fake — báo cáo'
FROM categories c
JOIN (SELECT 1 AS n UNION SELECT 2) n ON TRUE
WHERE c.name = 'Lịch sử' AND c.deleted_at IS NULL
LIMIT 2;

-- ---------------------------------------------------------------------------
-- Thêm độc giả ACTIVE (để top độc giả có nhiều hàng khác nhau)
-- ---------------------------------------------------------------------------
INSERT INTO members (member_code, full_name, email, phone, address, join_date, expiry_date, status, lost_book_count)
VALUES
  ('TV2026FAKE01', 'Nguyễn Văn Demo', 'demo01@test.local', '0912345678', 'HN', DATE(CONCAT(@report_year, '-02-01')), DATE(CONCAT(@report_year + 2, '-02-01')), 'ACTIVE', 0),
  ('TV2026FAKE02', 'Trần Thị Alpha', 'demo02@test.local', '0912345679', 'HCM', DATE(CONCAT(@report_year, '-02-05')), DATE(CONCAT(@report_year + 2, '-02-05')), 'ACTIVE', 0),
  ('TV2026FAKE03', 'Lê Minh Beta', 'demo03@test.local', '0912345680', 'DN', DATE(CONCAT(@report_year, '-03-01')), DATE(CONCAT(@report_year + 2, '-03-01')), 'ACTIVE', 0),
  ('TV2026FAKE04', 'Phạm Thu Gamma', 'demo04@test.local', '0912345681', 'CT', DATE(CONCAT(@report_year, '-03-10')), DATE(CONCAT(@report_year + 2, '-03-10')), 'ACTIVE', 0),
  ('TV2026FAKE05', 'Hoàng An Delta', 'demo05@test.local', '0912345682', 'HP', DATE(CONCAT(@report_year, '-04-01')), DATE(CONCAT(@report_year + 2, '-04-01')), 'ACTIVE', 0);

-- ---------------------------------------------------------------------------
-- Phiếu mượn: trải các tháng trong @report_year + lặp lại để có TOP sách / TOP độc giả rõ ràng
-- borrow_date là ngày ghi nhận mượn — ReportService đếm theo MONTH(borrow_date).
-- ---------------------------------------------------------------------------

-- Tiện ích: một phiếu RETURNED trong tháng `mo`
INSERT INTO borrow_records (member_id, book_id, borrow_date, due_date, return_date, status, notes)
SELECT mem.id, bk.id,
       DATE(CONCAT(@report_year, '-', LPAD(mo.mo, 2, '0'), '-', LPAD(10 + (seq.seq MOD 18), 2, '0'))),
       DATE_ADD(DATE(CONCAT(@report_year, '-', LPAD(mo.mo, 2, '0'), '-', LPAD(10 + (seq.seq MOD 18), 2, '0'))), INTERVAL 14 DAY),
       DATE_ADD(DATE(CONCAT(@report_year, '-', LPAD(mo.mo, 2, '0'), '-', LPAD(10 + (seq.seq MOD 18), 2, '0'))), INTERVAL 10 DAY),
       'RETURNED',
       'Fake báo cáo'
FROM (SELECT 1 mo UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6
      UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12) mo
JOIN (SELECT 0 seq UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) seq ON TRUE
JOIN members mem ON mem.member_code IN ('TV2025001', 'TV2026FAKE01', 'TV2026FAKE02', 'TV2026FAKE03', 'TV2026FAKE04', 'TV2026FAKE05')
JOIN books bk ON bk.title IN ('Đắc nhân tâm', 'Sapiens', 'Demo VH 1', 'Demo VH 2', 'Demo KH 1')
WHERE MOD(mo.mo + seq.seq + ASCII(SUBSTRING(mem.member_code, 1, 1)), 7) = 0;

-- Thêm khối lượng có chủ đích: sách được mượn nhiều nhất + độc giả mượn nhiều nhất
INSERT INTO borrow_records (member_id, book_id, borrow_date, due_date, return_date, status, notes)
SELECT (SELECT id FROM members WHERE member_code = 'TV2026FAKE01' LIMIT 1),
       (SELECT id FROM books WHERE title = 'Đắc nhân tâm' LIMIT 1),
       DATE(CONCAT(@report_year, '-01-', LPAD(1 + seq.n, 2, '0'))),
       DATE(CONCAT(@report_year, '-02-', LPAD(1 + seq.n, 2, '0'))),
       DATE(CONCAT(@report_year, '-01-', LPAD(3 + seq.n, 2, '0'))),
       'RETURNED',
       'Fake — hot book'
FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
      UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19
      UNION SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24) seq;

INSERT INTO borrow_records (member_id, book_id, borrow_date, due_date, return_date, status, notes)
SELECT (SELECT id FROM members WHERE member_code = 'TV2026FAKE02' LIMIT 1),
       (SELECT id FROM books WHERE title = 'Demo VH 1' LIMIT 1),
       DATE(CONCAT(@report_year, '-03-', LPAD(1 + seq.n, 2, '0'))),
       DATE(CONCAT(@report_year, '-04-', LPAD(1 + seq.n, 2, '0'))),
       DATE(CONCAT(@report_year, '-03-', LPAD(5 + seq.n, 2, '0'))),
       'RETURNED',
       'Fake — active reader'
FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
      UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19) seq;

INSERT INTO borrow_records (member_id, book_id, borrow_date, due_date, return_date, status, notes)
SELECT (SELECT id FROM members WHERE member_code = 'TV2025001' LIMIT 1),
       (SELECT id FROM books WHERE title = 'Sapiens' LIMIT 1),
       DATE(CONCAT(@report_year, '-06-', LPAD(1 + seq.n, 2, '0'))),
       DATE(CONCAT(@report_year, '-07-', LPAD(1 + seq.n, 2, '0'))),
       DATE(CONCAT(@report_year, '-06-', LPAD(8 + seq.n, 2, '0'))),
       'RETURNED',
       'Fake — combo'
FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
      UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15) seq;

-- Vài phiếu các tháng cuối năm (đảm bảo T10–T12 có cột trên chart)
INSERT INTO borrow_records (member_id, book_id, borrow_date, due_date, return_date, status, notes)
SELECT (SELECT id FROM members WHERE member_code = 'TV2026FAKE03' LIMIT 1),
       (SELECT id FROM books WHERE title = 'Demo LS 1' LIMIT 1),
       bd.borrow_date,
       DATE_ADD(bd.borrow_date, INTERVAL 14 DAY),
       DATE_ADD(bd.borrow_date, INTERVAL 9 DAY),
       'RETURNED',
       'Fake — late months'
FROM (
  SELECT DATE(CONCAT(@report_year, '-10-15')) AS borrow_date
  UNION ALL SELECT DATE(CONCAT(@report_year, '-11-12'))
  UNION ALL SELECT DATE(CONCAT(@report_year, '-12-08'))
) bd;
