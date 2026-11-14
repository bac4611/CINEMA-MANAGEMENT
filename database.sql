/*
  MOVIETICKETDB - TOAN BO CAU TRUC DATABASE
  Gom schema goc + BookingHolds + BookingSeatHolds + BookingComboHolds
  + PaymentIntents (phien ban hop nhat) + StockQuantity.
  Chay tren SQL Server / SSMS, danh cho DATABASE MOI CHUA CO CAC BANG.
  KHONG chay lai tren DB da ton tai cac bang (CREATE TABLE se bao loi).
  KHONG co lenh DROP TABLE / xoa du lieu.
*/

if not exists (select * from sys.databases where name = 'MovieTicketDB')
begin
    create database MovieTicketDB;
    print N'Đã tạo mới database';
end
else
begin
    print N'Database đã tồn tại, skip tạo database';
end
go

use MovieTicketDB;
go

-- BẢNG GENRES: Danh mục thể loại phim.
create table Genres (
  GenreID  int          primary key identity(1, 1),
  Name     nvarchar(100) NOT NULL
)
go

-- BẢNG COUNTRIES: Danh mục quốc gia sản xuất.
create table Countries (
  CountryID   int          primary key identity(1, 1),
  CountryName nvarchar(100) NOT NULL
)
go

-- BẢNG LANGUAGES: Danh mục ngôn ngữ gốc của phim.
create table Languages (
  LanguageID   int          primary key identity(1, 1),
  LanguageName nvarchar(100) NOT NULL
)
go

-- BẢNG PERSONS: Danh mục nhân sự điện ảnh (diễn viên/đạo diễn).
create table Persons (
  PersonID int          primary key identity(1, 1),
  FullName nvarchar(150) NOT NULL
)
go

-- ==========================================
-- 2. PHIM
-- ==========================================

-- BẢNG MOVIES: Thông tin cơ bản của phim, poster, trailer, phân loại tuổi và xuất xứ.
create table Movies (
  MovieID     int           primary key identity(1, 1),
  Title      nvarchar(200) NOT NULL,
  ReleaseDate datetime      NOT NULL,
  AgeRating   nvarchar(10)  default '--',
  Duration    smallint      NOT NULL,
  Synopsis    nvarchar(1000) default N'Chưa có tóm tắt',
  PosterURL   varchar(1000) NOT NULL,
  Trailer     varchar(1000) NOT NULL,
  CountryID   int,
  LanguageID  int,

CONSTRAINT FK_Movies_Countries FOREIGN KEY (CountryID) REFERENCES Countries (CountryID),
CONSTRAINT FK_Movies_Languages FOREIGN KEY (LanguageID) REFERENCES Languages (LanguageID)
)
go

-- BẢNG MOVIEGENRES: Liên kết nhiều-nhiều giữa phim và thể loại.
create table MovieGenres (
  MovieID int,
  GenreID int,
  primary key (MovieID, GenreID),

CONSTRAINT FK_MovieGenres_Movies FOREIGN KEY (MovieID) REFERENCES [Movies] (MovieID),
CONSTRAINT FK_MovieGenres_Genres FOREIGN KEY (GenreID) REFERENCES [Genres] (GenreID)
)
go

-- BẢNG MOVIEDIRECTORS: Liên kết phim và đạo diễn.
create table MovieDirectors (
  MovieID  int,
  PersonID int,
  primary key (MovieID, PersonID),

CONSTRAINT FK_MovieDirectors_Movies FOREIGN KEY (MovieID) REFERENCES Movies (MovieID),
CONSTRAINT FK_MovieDirectors_Persons FOREIGN KEY (PersonID) REFERENCES Persons (PersonID)
)
go

-- BẢNG MOVIECASTS: Danh sách diễn viên và nhân vật theo từng phim.
create table MovieCasts (
  MovieID       int,
  PersonID      int,
  CharacterName nvarchar(150),
  primary key (MovieID, PersonID),

CONSTRAINT FK_MovieCasts_Movies FOREIGN KEY (MovieID) REFERENCES Movies (MovieID),
CONSTRAINT FK_MovieCasts_Persons FOREIGN KEY (PersonID) REFERENCES Persons (PersonID)
)
go

-- ==========================================
-- 3. RẠP — PHÒNG — GHẾ
-- ==========================================

-- BẢNG ROOMS: Danh sách phòng chiếu.
create table Rooms (
  RoomID   int PRIMARY KEY IDENTITY(1, 1),
  RoomName nvarchar(100) NOT NULL
)
GO

-- BẢNG SEATTYPEPRICING: Hệ số nhân giá cho từng loại ghế.
create table SeatTypePricing (
  SeatType   varchar(10) NOT NULL PRIMARY KEY,
  Multiplier decimal(4,2) NOT NULL DEFAULT (1.0),
  CONSTRAINT CK_SeatTypePricing_SeatType CHECK (SeatType IN ('Regular', 'VIP', 'Couple'))
)
GO

-- BẢNG SEATS: Danh sách ghế, loại ghế và trạng thái trong từng phòng.
create table Seats (
  SeatID   int          PRIMARY KEY IDENTITY(1, 1),
  RoomID   int          NOT NULL,
  SeatCode nvarchar(10) NOT NULL,
  SeatType varchar(10)  NOT NULL,
  SeatStatus varchar(50) NOT NULL,
CONSTRAINT CK_Seats_SeatType CHECK (SeatType IN ('Regular', 'VIP', 'Couple')),
CONSTRAINT CK_Seats_Status CHECK (SeatStatus IN ('Order', 'Not Order', 'Orderring')),
CONSTRAINT FK_Seats_Rooms FOREIGN KEY (RoomID) REFERENCES Rooms (RoomID),
CONSTRAINT FK_Seats_SeatTypePricing FOREIGN KEY (SeatType) REFERENCES SeatTypePricing (SeatType)
)
GO

-- ==========================================
-- 4. SUẤT CHIẾU
-- ==========================================

-- BẢNG SHOWTIMES: Lịch chiếu phim, thời gian, phòng chiếu và giá vé cơ bản.
CREATE TABLE Showtimes (
    ShowtimeID int PRIMARY KEY IDENTITY(1, 1),
    MovieID    int NOT NULL,
    RoomID     int NOT NULL,
    [Date]     date NOT NULL,
    StartTime  datetime NOT NULL,
    EndTime    datetime NOT NULL,
    BasePrice  decimal(10, 2) NOT NULL,


    CONSTRAINT FK_Showtimes_Movies FOREIGN KEY (MovieID) REFERENCES Movies (MovieID),
    CONSTRAINT FK_Showtimes_Rooms FOREIGN KEY (RoomID) REFERENCES Rooms (RoomID),

    CONSTRAINT UQ_Showtimes_Room_StartTime UNIQUE (RoomID, StartTime),

    CONSTRAINT CK_Showtimes_Time CHECK (EndTime > StartTime),
    CONSTRAINT CK_Showtimes_BasePrice CHECK (BasePrice >= 0),
    CONSTRAINT CK_Showtimes_Date CHECK ([Date] = CAST(StartTime AS date))
);
GO

-- ==========================================
-- 5. NGƯỜI DÙNG
-- ==========================================

-- BẢNG USERS: Tài khoản khách hàng/nhân viên/admin và dữ liệu xác thực.
create table Users (
  UserID       int          PRIMARY KEY IDENTITY(1, 1),
  FullName    nvarchar(150) NOT NULL,
  Email        varchar(200)  UNIQUE NOT NULL,
  PasswordHash varchar(512)  NULL,
  PhoneNumber  varchar(15),
  DOB          date,
  Status       bit           DEFAULT (1),
  [Role]       nvarchar(20)  NOT NULL DEFAULT N'KhachHang',
    EmailConfirmed BIT NOT NULL
        CONSTRAINT DF_Users_EmailConfirmed DEFAULT 0,

    EmailVerificationLastSentAt DATETIME2 NULL,

    EmailVerificationTokenExpiresAt DATETIME2 NULL,

    EmailVerificationTokenHash VARCHAR(64) NULL,

    ExternalProvider NVARCHAR(50) NULL,

    ExternalProviderKey NVARCHAR(200) NULL,

CONSTRAINT CK_Users_Role CHECK ([Role] IN ('Admin', 'Staff', 'KhachHang'))
)
GO

-- ==========================================
-- 6. ĐẶT VÉ
-- ==========================================

-- BẢNG BOOKINGS: Đơn đặt vé, người đặt, tổng tiền và trạng thái.
create table Bookings (
  BookingID   int           PRIMARY KEY IDENTITY(1, 1),
  UserID      int           NOT NULL,
  BookingDate datetime      NOT NULL,
  TotalAmount decimal(10,2) NOT NULL,
  Status      nvarchar(10)  NOT NULL DEFAULT 'Pending',

CONSTRAINT CK_Bookings_Status CHECK (Status IN ('Pending', 'Confirmed', 'Cancelled')),
CONSTRAINT FK_Bookings_Users FOREIGN KEY (UserID) REFERENCES Users (UserID)
)
GO

-- BẢNG TICKETS: Vé cụ thể liên kết đơn đặt, suất chiếu và ghế.
create table Tickets (
  TicketID   int         PRIMARY KEY IDENTITY(1, 1),
  BookingID  int         NOT NULL,
  ShowtimeID int         NOT NULL,
  SeatID     int         NOT NULL,
  TicketCode varchar(100) UNIQUE NOT NULL,

CONSTRAINT FK_Tickets_Bookings FOREIGN KEY (BookingID) REFERENCES Bookings (BookingID),
CONSTRAINT FK_Tickets_Showtimes FOREIGN KEY (ShowtimeID) REFERENCES Showtimes (ShowtimeID),
CONSTRAINT FK_Tickets_Seats FOREIGN KEY (SeatID) REFERENCES Seats (SeatID)
)
GO

-- ==========================================
-- 7. COMBO BẮP NƯỚC
-- ==========================================

-- BẢNG COMBOS: Combo bắp nước, giá, số lượng và số lượng tồn/giữ.
create table Combos (
  ComboID    int           PRIMARY KEY IDENTITY(1, 1),
  ComboName  nvarchar(150) NOT NULL,
  ComboPrice decimal(10,2) NOT NULL,
  Quantity INT NULL,
  StockQuantity INT NOT NULL CONSTRAINT DF_Combos_StockQuantity DEFAULT (0),
  CONSTRAINT CK_Combos_StockQuantity CHECK (StockQuantity >= 0)
)
GO

-- BẢNG BOOKINGCOMBOS: Combo được mua trong từng đơn, số lượng và giá chốt.
create table BookingCombos (
  BookingID int,
  ComboID   int,
  Quantity  int           NOT NULL DEFAULT (1),
  UnitPrice decimal(10,2) NOT NULL,
  PRIMARY KEY (BookingID, ComboID),

CONSTRAINT FK_BookingCombos_Bookings FOREIGN KEY (BookingID) REFERENCES Bookings (BookingID),
CONSTRAINT FK_BookingCombos_Combos FOREIGN KEY (ComboID) REFERENCES Combos (ComboID)
)
GO

-- ==========================================
-- 8. THANH TOÁN
-- ==========================================

-- BẢNG PAYMENTMETHODS: Danh mục các phương thức thanh toán.
create table PaymentMethods (
  MethodID   int          PRIMARY KEY IDENTITY(1, 1),
  MethodName nvarchar(100) NOT NULL
)
GO

-- BẢNG PAYMENTS: Thanh toán đã ghi nhận cho đơn đặt vé.
create table Payments (
  PaymentID   int           PRIMARY KEY IDENTITY(1, 1),
  BookingID   int           NOT NULL,
  MethodID    int           NOT NULL,
  Amount      decimal(10,2) NOT NULL,
  PaymentDate datetime      NOT NULL,
  Status      nvarchar(10)  NOT NULL DEFAULT 'Pending',

CONSTRAINT CK_Payments_Status CHECK (Status IN ('Pending', 'Success', 'Failed')),
CONSTRAINT FK_Payments_Bookings FOREIGN KEY (BookingID) REFERENCES Bookings (BookingID),
CONSTRAINT FK_Payments_PaymentMethods FOREIGN KEY (MethodID) REFERENCES PaymentMethods (MethodID)
)
GO




-- ==========================================================
-- BẢNG BOOKINGHOLDS: Phiên giữ chỗ tạm thời trước khi thanh toán.
-- ==========================================================
CREATE TABLE dbo.BookingHolds (
    BookingHoldID UNIQUEIDENTIFIER NOT NULL,
    MovieID INT NOT NULL,
    ShowtimeID INT NULL,
    UserID INT NULL,
    Status NVARCHAR(20) NOT NULL CONSTRAINT DF_BookingHolds_Status DEFAULT N'Pending',
    CreatedAtUtc DATETIME2(7) NOT NULL CONSTRAINT DF_BookingHolds_CreatedAtUtc DEFAULT SYSUTCDATETIME(),
    ExpiresAtUtc DATETIME2(7) NOT NULL,
    RowVersion ROWVERSION NOT NULL,
    CONSTRAINT PK_BookingHolds PRIMARY KEY (BookingHoldID),
    CONSTRAINT FK_BookingHolds_Movies FOREIGN KEY (MovieID) REFERENCES dbo.Movies(MovieID),
    CONSTRAINT FK_BookingHolds_Showtimes FOREIGN KEY (ShowtimeID) REFERENCES dbo.Showtimes(ShowtimeID),
    CONSTRAINT FK_BookingHolds_Users FOREIGN KEY (UserID) REFERENCES dbo.Users(UserID),
    CONSTRAINT CK_BookingHolds_Status CHECK (Status IN
        (N'Pending',N'PaymentPending',N'Processing',N'Releasing',N'Completed',N'Cancelled',N'Expired'))
);
GO

-- ==========================================================
-- BẢNG BOOKINGSEATHOLDS: Ghế tạm giữ; mỗi ghế chỉ được giữ
-- bởi một phiên tại cùng một suất chiếu.
-- ==========================================================
CREATE TABLE dbo.BookingSeatHolds (
    BookingHoldID UNIQUEIDENTIFIER NOT NULL,
    ShowtimeID INT NOT NULL,
    SeatID INT NOT NULL,
    UnitPrice DECIMAL(10,2) NOT NULL,
    HeldAtUtc DATETIME2(7) NOT NULL CONSTRAINT DF_BookingSeatHolds_HeldAtUtc DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_BookingSeatHolds PRIMARY KEY (ShowtimeID, SeatID),
    CONSTRAINT FK_BookingSeatHolds_BookingHolds FOREIGN KEY (BookingHoldID) REFERENCES dbo.BookingHolds(BookingHoldID),
    CONSTRAINT FK_BookingSeatHolds_Showtimes FOREIGN KEY (ShowtimeID) REFERENCES dbo.Showtimes(ShowtimeID),
    CONSTRAINT FK_BookingSeatHolds_Seats FOREIGN KEY (SeatID) REFERENCES dbo.Seats(SeatID),
    CONSTRAINT CK_BookingSeatHolds_UnitPrice CHECK (UnitPrice >= 0)
);
GO

-- ==========================================================
-- BẢNG BOOKINGCOMBOHOLDS: Combo đồ ăn, nước uống được giữ
-- theo phiên đặt vé trước khi khách thanh toán.
-- ==========================================================
CREATE TABLE dbo.BookingComboHolds (
    BookingHoldID UNIQUEIDENTIFIER NOT NULL,
    ComboID INT NOT NULL,
    Quantity INT NOT NULL,
    UnitPrice DECIMAL(10,2) NOT NULL,
    CONSTRAINT PK_BookingComboHolds PRIMARY KEY (BookingHoldID, ComboID),
    CONSTRAINT FK_BookingComboHolds_BookingHolds FOREIGN KEY (BookingHoldID) REFERENCES dbo.BookingHolds(BookingHoldID),
    CONSTRAINT FK_BookingComboHolds_Combos FOREIGN KEY (ComboID) REFERENCES dbo.Combos(ComboID),
    CONSTRAINT CK_BookingComboHolds_Quantity CHECK (Quantity >= 1),
    CONSTRAINT CK_BookingComboHolds_UnitPrice CHECK (UnitPrice >= 0)
);
GO

-- ==========================================================
-- BẢNG PAYMENTINTENTS: Yêu cầu thanh toán, tham chiếu SePay,
-- thông tin ghế/combo và trạng thái thanh toán chờ xử lý.
-- Gộp cấu trúc cũ và bổ sung BookingHoldID, UNIQUE và indexes.
-- ==========================================================
CREATE TABLE dbo.PaymentIntents (
    PaymentIntentID INT IDENTITY(1,1) NOT NULL,
    UserID INT NOT NULL,
    MovieID INT NOT NULL,
    ShowtimeID INT NOT NULL,
    BookingID INT NULL,
    BookingHoldID UNIQUEIDENTIFIER NULL,
    PaymentReference NVARCHAR(100) NOT NULL,
    ExpectedAmount DECIMAL(10,2) NOT NULL,
    Status NVARCHAR(20) NOT NULL CONSTRAINT DF_PaymentIntents_Status DEFAULT N'Pending',
    SelectedSeatCodes NVARCHAR(500) NOT NULL,
    SelectedCombosJson NVARCHAR(MAX) NULL,
    SePayTransactionID BIGINT NULL,
    SePayReferenceCode NVARCHAR(255) NULL,
    SePayContent NVARCHAR(MAX) NULL,
    WebhookPayload NVARCHAR(MAX) NULL,
    CreatedAtUtc DATETIME2(7) NOT NULL CONSTRAINT DF_PaymentIntents_CreatedAtUtc DEFAULT SYSUTCDATETIME(),
    ExpiresAtUtc DATETIME2(7) NOT NULL,
    PaidAtUtc DATETIME2(7) NULL,
    CONSTRAINT PK_PaymentIntents PRIMARY KEY (PaymentIntentID),
    CONSTRAINT UQ_PaymentIntents_PaymentReference UNIQUE (PaymentReference),
    CONSTRAINT FK_PaymentIntents_Users FOREIGN KEY (UserID) REFERENCES dbo.Users(UserID),
    CONSTRAINT FK_PaymentIntents_Movies FOREIGN KEY (MovieID) REFERENCES dbo.Movies(MovieID),
    CONSTRAINT FK_PaymentIntents_Showtimes FOREIGN KEY (ShowtimeID) REFERENCES dbo.Showtimes(ShowtimeID),
    CONSTRAINT FK_PaymentIntents_Bookings FOREIGN KEY (BookingID) REFERENCES dbo.Bookings(BookingID),
    CONSTRAINT FK_PaymentIntents_BookingHolds FOREIGN KEY (BookingHoldID) REFERENCES dbo.BookingHolds(BookingHoldID),
    CONSTRAINT CK_PaymentIntents_ExpectedAmount CHECK (ExpectedAmount >= 0),
    CONSTRAINT CK_PaymentIntents_Status CHECK (Status IN
        (N'Pending',N'Processing',N'Success',N'Failed',N'Expired'))
);
GO

-- ==========================================
-- 9. INDEX
-- ==========================================
CREATE UNIQUE INDEX UQ_Tickets_Showtime_Seat
  ON Tickets (ShowtimeID, SeatID)
GO

-- INDEX: Tối ưu tìm kiếm phiên giữ chỗ và giao dịch đang chờ.
CREATE INDEX IX_BookingHolds_Status_ExpiresAtUtc ON dbo.BookingHolds(Status, ExpiresAtUtc);
CREATE INDEX IX_BookingHolds_UserID ON dbo.BookingHolds(UserID);
CREATE INDEX IX_BookingHolds_ShowtimeID ON dbo.BookingHolds(ShowtimeID);
CREATE INDEX IX_BookingSeatHolds_BookingHoldID ON dbo.BookingSeatHolds(BookingHoldID);
CREATE INDEX IX_BookingSeatHolds_SeatID ON dbo.BookingSeatHolds(SeatID);
CREATE INDEX IX_BookingComboHolds_ComboID ON dbo.BookingComboHolds(ComboID);
CREATE UNIQUE INDEX UX_PaymentIntents_SePayTransactionID ON dbo.PaymentIntents(SePayTransactionID)
    WHERE SePayTransactionID IS NOT NULL;
CREATE INDEX IX_PaymentIntents_BookingHoldID ON dbo.PaymentIntents(BookingHoldID);
CREATE INDEX IX_PaymentIntents_UserID_Status ON dbo.PaymentIntents(UserID, Status);
CREATE INDEX IX_PaymentIntents_Status_ExpiresAtUtc ON dbo.PaymentIntents(Status, ExpiresAtUtc);
GO

-- ==========================================
-- 10. EXTENDED PROPERTIES (MÔ TẢ CỘT)
-- ==========================================

EXEC sp_addextendedproperty
  @name      = N'Column_Description',
  @value     = N'Thời lượng phim tính bằng số phút',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'Movies',
  @level2type = N'Column', @level2name = N'Duration'
GO

EXEC sp_addextendedproperty
  @name      = N'Column_Description',
  @value     = N'Nước sản xuất gốc',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'Movies',
  @level2type = N'Column', @level2name = N'CountryID'
GO

EXEC sp_addextendedproperty
  @name      = N'Column_Description',
  @value     = N'Ngôn ngữ gốc',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'Movies',
  @level2type = N'Column', @level2name = N'LanguageID'
GO

EXEC sp_addextendedproperty
  @name      = N'Column_Description',
  @value     = N'Mã ghế, ví dụ: A1, F05',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'Seats',
  @level2type = N'Column', @level2name = N'SeatCode'
GO

EXEC sp_addextendedproperty
  @name      = N'Column_Description',
  @value     = N'Giá vé cơ bản, nhân với Multiplier ra giá thực',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'Showtimes',
  @level2type = N'Column', @level2name = N'BasePrice'
GO

EXEC sp_addextendedproperty
  @name      = N'Column_Description',
  @value     = N'Ngày chiếu, dùng để lọc lịch chiếu theo ngày',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'Showtimes',
  @level2type = N'Column', @level2name = N'Date'
GO

EXEC sp_addextendedproperty
  @name      = N'Column_Description',
  @value     = N'Ngày giờ bắt đầu suất chiếu (bao gồm cả ngày)',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'Showtimes',
  @level2type = N'Column', @level2name = N'StartTime'
GO

EXEC sp_addextendedproperty
  @name      = N'Column_Description',
  @value     = N'Dùng để kiểm tra điều kiện tuổi T13/T18',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'Users',
  @level2type = N'Column', @level2name = N'DOB'
GO

EXEC sp_updateextendedproperty 
    @name = N'Column_Description', 
    @value = N'Kiểm tra điều kiện người dùng > 16 tuổi mới được phép tạo tài khoản / đặt vé', 
    @level0type = N'SCHEMA', @level0name = N'dbo', 
    @level1type = N'TABLE',  @level1name = N'Users', 
    @level2type = N'COLUMN', @level2name = N'DOB';
GO

EXEC sp_addextendedproperty
  @name      = N'Column_Description',
  @value     = N'1 = hoạt động, 0 = bị khoá',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'Users',
  @level2type = N'Column', @level2name = N'Status'
GO

EXEC sp_addextendedproperty
  @name      = N'Column_Description',
  @value     = N'Vai trò tài khoản: Admin, Staff hoặc KhachHang',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'Users',
  @level2type = N'Column', @level2name = N'Role'
GO

EXEC sp_addextendedproperty
  @name = N'Column_Description',
  @value     = N'Snapshot giá combo tại thời điểm đặt vé',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'BookingCombos',
  @level2type = N'Column', @level2name = N'UnitPrice'
GO

EXEC sp_addextendedproperty
  @name      = N'Column_Description',
  @value     = N'Ví dụ: VNPay, MoMo, Visa',
  @level0type = N'Schema', @level0name = N'dbo',
  @level1type = N'Table',  @level1name = N'PaymentMethods',
  @level2type = N'Column', @level2name = N'MethodName'
GO


-- ==========================================
-- REVIEW / COMMENT / RATING PHIM
-- ==========================================

-- BẢNG REVIEWS: Bình luận, đánh giá sao, trả lời bình luận về phim.
CREATE TABLE Reviews (
    ReviewID INT PRIMARY KEY IDENTITY(1, 1),

    -- Tài khoản viết bình luận
    UserID INT NOT NULL,

    -- Phim được bình luận
    MovieID INT NOT NULL,

    -- NULL: bình luận gốc
    -- Có giá trị: phản hồi một bình luận khác
    ParentReviewID INT NULL,

    -- Nội dung bình luận
    Content NVARCHAR(2000) NOT NULL,

    -- Số sao từ 0.00 đến 5.00
    Rating DECIMAL(3,2) NULL,

    CreatedAt DATETIME2 NOT NULL
        CONSTRAINT DF_Reviews_CreatedAt
        DEFAULT SYSUTCDATETIME(),

    UpdatedAt DATETIME2 NULL,

    Status NVARCHAR(20) NOT NULL
        CONSTRAINT DF_Reviews_Status
        DEFAULT N'Visible',

    CONSTRAINT FK_Reviews_Users
        FOREIGN KEY (UserID)
        REFERENCES Users(UserID),

    CONSTRAINT FK_Reviews_Movies
        FOREIGN KEY (MovieID)
        REFERENCES Movies(MovieID),

    CONSTRAINT FK_Reviews_Parent
        FOREIGN KEY (ParentReviewID)
        REFERENCES Reviews(ReviewID),

    CONSTRAINT CK_Reviews_Rating
        CHECK (
            Rating IS NULL
            OR Rating BETWEEN 0.00 AND 5.00
        ),

    CONSTRAINT CK_Reviews_Content
        CHECK (LEN(LTRIM(RTRIM(Content))) > 0),

    CONSTRAINT CK_Reviews_Status
        CHECK (Status IN (N'Visible', N'Hidden', N'Deleted'))
);
GO

CREATE INDEX IX_Reviews_Movie_CreatedAt
ON Reviews(MovieID, CreatedAt DESC);
GO

CREATE INDEX IX_Reviews_ParentReviewID
ON Reviews(ParentReviewID);
GO

CREATE INDEX IX_Reviews_UserID
ON Reviews(UserID);
GO