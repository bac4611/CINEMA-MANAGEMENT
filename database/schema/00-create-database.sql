USE master;
GO

IF DB_ID(N'CinemaManagement') IS NULL
BEGIN
    CREATE DATABASE CinemaManagement;
END;
GO

USE CinemaManagement;
GO
