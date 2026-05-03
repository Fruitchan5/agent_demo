package cn.edu.agent.tool.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WorkspaceEnv Security Tests")
class WorkspaceEnvTest {

    @Nested
    @DisplayName("Valid Path Tests")
    class ValidPathTests {

        @Test
        @DisplayName("Should accept simple relative path")
        void testSimpleRelativePath() {
            Path result = WorkspaceEnv.safePath("test.txt");
            assertThat(result).isNotNull();
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
            assertThat(result.toString()).endsWith("test.txt");
        }

        @Test
        @DisplayName("Should accept nested relative path")
        void testNestedRelativePath() {
            Path result = WorkspaceEnv.safePath("src/main/java/Test.java");
            assertThat(result).isNotNull();
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
        }

        @Test
        @DisplayName("Should accept path with dots that stays within workspace")
        void testPathWithDotsStayingInside() {
            Path result = WorkspaceEnv.safePath("src/../test.txt");
            assertThat(result).isNotNull();
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
        }

        @Test
        @DisplayName("Should accept current directory reference")
        void testCurrentDirectory() {
            Path result = WorkspaceEnv.safePath(".");
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(WorkspaceEnv.WORKDIR);
        }

        @Test
        @DisplayName("Should accept path with current directory in middle")
        void testPathWithCurrentDirectory() {
            Path result = WorkspaceEnv.safePath("src/./main/test.txt");
            assertThat(result).isNotNull();
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
        }

        @Test
        @DisplayName("Should accept empty string as current directory")
        void testEmptyString() {
            Path result = WorkspaceEnv.safePath("");
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(WorkspaceEnv.WORKDIR);
        }
    }

    @Nested
    @DisplayName("Path Traversal Attack Prevention Tests")
    class PathTraversalAttackTests {

        @Test
        @DisplayName("Should reject simple parent directory escape")
        void testSimpleParentEscape() {
            assertThatThrownBy(() -> WorkspaceEnv.safePath("../outside.txt"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("严重安全警告")
                    .hasMessageContaining("路径尝试逃逸工作区");
        }

        @Test
        @DisplayName("Should reject multiple parent directory escapes")
        void testMultipleParentEscapes() {
            assertThatThrownBy(() -> WorkspaceEnv.safePath("../../outside.txt"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("严重安全警告");
        }

        @Test
        @DisplayName("Should reject deep parent directory escape")
        void testDeepParentEscape() {
            assertThatThrownBy(() -> WorkspaceEnv.safePath("../../../../../etc/passwd"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("严重安全警告");
        }

        @Test
        @DisplayName("Should reject escape hidden in nested path")
        void testHiddenEscape() {
            assertThatThrownBy(() -> WorkspaceEnv.safePath("src/../../outside.txt"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("严重安全警告");
        }

        @Test
        @DisplayName("Should reject absolute path outside workspace")
        void testAbsolutePathOutside() {
            String outsidePath = System.getProperty("os.name").toLowerCase().contains("win") 
                    ? "C:\\Windows\\System32\\config" 
                    : "/etc/passwd";
            
            assertThatThrownBy(() -> WorkspaceEnv.safePath(outsidePath))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("严重安全警告");
        }

        @Test
        @DisplayName("Should reject path with backslash escape on Windows")
        void testBackslashEscape() {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                assertThatThrownBy(() -> WorkspaceEnv.safePath("..\\outside.txt"))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("严重安全警告");
            }
        }

        @Test
        @DisplayName("Should reject mixed slash escape attempts")
        void testMixedSlashEscape() {
            assertThatThrownBy(() -> WorkspaceEnv.safePath("src/../../../outside.txt"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("严重安全警告");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle path with spaces")
        void testPathWithSpaces() {
            Path result = WorkspaceEnv.safePath("my folder/test file.txt");
            assertThat(result).isNotNull();
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
        }

        @Test
        @DisplayName("Should handle path with special characters")
        void testPathWithSpecialCharacters() {
            Path result = WorkspaceEnv.safePath("test-file_123.txt");
            assertThat(result).isNotNull();
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
        }

        @Test
        @DisplayName("Should handle path with unicode characters")
        void testPathWithUnicode() {
            Path result = WorkspaceEnv.safePath("测试文件.txt");
            assertThat(result).isNotNull();
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
        }

        @Test
        @DisplayName("Should handle deeply nested path")
        void testDeeplyNestedPath() {
            Path result = WorkspaceEnv.safePath("a/b/c/d/e/f/g/h/i/j/test.txt");
            assertThat(result).isNotNull();
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
        }

        @Test
        @DisplayName("Should handle path with trailing slash")
        void testPathWithTrailingSlash() {
            Path result = WorkspaceEnv.safePath("src/main/");
            assertThat(result).isNotNull();
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
        }

        @Test
        @DisplayName("Should handle path with multiple consecutive slashes")
        void testPathWithMultipleSlashes() {
            Path result = WorkspaceEnv.safePath("src//main///test.txt");
            assertThat(result).isNotNull();
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
        }
    }

    @Nested
    @DisplayName("WORKDIR Constant Tests")
    class WorkdirTests {

        @Test
        @DisplayName("WORKDIR should be absolute")
        void testWorkdirIsAbsolute() {
            assertThat(WorkspaceEnv.WORKDIR.isAbsolute()).isTrue();
        }

        @Test
        @DisplayName("WORKDIR should be normalized")
        void testWorkdirIsNormalized() {
            assertThat(WorkspaceEnv.WORKDIR).isEqualTo(WorkspaceEnv.WORKDIR.normalize());
        }

        @Test
        @DisplayName("WORKDIR should match user.dir")
        void testWorkdirMatchesUserDir() {
            Path expectedWorkdir = Paths.get(System.getProperty("user.dir"))
                    .toAbsolutePath()
                    .normalize();
            assertThat(WorkspaceEnv.WORKDIR).isEqualTo(expectedWorkdir);
        }
    }

    @Nested
    @DisplayName("Return Value Tests")
    class ReturnValueTests {

        @Test
        @DisplayName("Should return absolute path")
        void testReturnsAbsolutePath() {
            Path result = WorkspaceEnv.safePath("test.txt");
            assertThat(result.isAbsolute()).isTrue();
        }

        @Test
        @DisplayName("Should return normalized path")
        void testReturnsNormalizedPath() {
            Path result = WorkspaceEnv.safePath("src/./main/../test.txt");
            assertThat(result).isEqualTo(result.normalize());
        }

        @Test
        @DisplayName("Should return path starting with WORKDIR")
        void testReturnsPathInWorkdir() {
            Path result = WorkspaceEnv.safePath("any/path/test.txt");
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
        }
    }

    @Nested
    @DisplayName("Security Boundary Tests")
    class SecurityBoundaryTests {

        @Test
        @DisplayName("Should accept path exactly at workspace root")
        void testPathAtRoot() {
            Path result = WorkspaceEnv.safePath(".");
            assertThat(result).isEqualTo(WorkspaceEnv.WORKDIR);
        }

        @Test
        @DisplayName("Should reject path exactly one level above workspace")
        void testPathOneLevelAbove() {
            assertThatThrownBy(() -> WorkspaceEnv.safePath(".."))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("严重安全警告");
        }

        @Test
        @DisplayName("Should accept complex path that resolves inside workspace")
        void testComplexPathInsideWorkspace() {
            Path result = WorkspaceEnv.safePath("a/b/c/../../d/e/../f/test.txt");
            assertThat(result).isNotNull();
            assertThat(result.startsWith(WorkspaceEnv.WORKDIR)).isTrue();
        }

        @Test
        @DisplayName("Should reject complex path that resolves outside workspace")
        void testComplexPathOutsideWorkspace() {
            assertThatThrownBy(() -> WorkspaceEnv.safePath("a/b/../../../../../../outside.txt"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("严重安全警告");
        }
    }
}
