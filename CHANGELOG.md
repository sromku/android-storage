# Changelog
All notable changes to this project will be documented in this file.

## [2.1.0] - 2017-07-01 
### Added
- Sample app

### Changed
- Refactored and changed package name to com.snatik.storage.
- Configuration class changed to by security configuration.
- All methods will work with absolute file and directory paths only.

### Removed
- Internal and External classes implementations. 
- Test and library is used in sample app.
- No more RuntimeException, but added logs and return boolean true/false upon execution.

### Fixed
- Random salt is generated on each app session.


## [1.2.1] - 2017-05-22
### Added
- Support for public directories.