USE MovieTicketDB;
GO

-- Add missing columns to an existing database without replacing its data.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.Users', 'PasswordResetTokenHash') IS NULL
    ALTER TABLE dbo.Users ADD PasswordResetTokenHash VARCHAR(64) NULL;

IF COL_LENGTH('dbo.Users', 'PasswordResetTokenExpiresAt') IS NULL
    ALTER TABLE dbo.Users ADD PasswordResetTokenExpiresAt DATETIME2 NULL;

IF COL_LENGTH('dbo.Users', 'PasswordResetLastSentAt') IS NULL
    ALTER TABLE dbo.Users ADD PasswordResetLastSentAt DATETIME2 NULL;

IF COL_LENGTH('dbo.Combos', 'StockQuantity') IS NULL
BEGIN
    ALTER TABLE dbo.Combos ADD StockQuantity INT NOT NULL
        CONSTRAINT DF_Combos_StockQuantity DEFAULT (0) WITH VALUES;
    EXEC(N'ALTER TABLE dbo.Combos WITH CHECK ADD CONSTRAINT CK_Combos_StockQuantity
        CHECK (StockQuantity >= 0)');
END;

COMMIT TRANSACTION;
GO
