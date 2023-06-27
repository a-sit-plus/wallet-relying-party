package at.asit.apps.terminal_sp.prototype.server.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OneToOneMappingTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void givenEmptyMapping_whenPutting5to7_thenGives5whenSecondIs7() {
        // arrange
        OneToOneMapping<Integer, Integer> mapping = new OneToOneMapping<>();
        Integer first = 5;
        Integer second = 7;

        // act
        mapping.put(first, second);

        // assert
        assertEquals(first, mapping.getBySecond(second));
    }

    @Test
    void givenEmptyMapping_whenPutting5to7_thenGives7whenFirstIs5() {
        // arrange
        OneToOneMapping<Integer, Integer> mapping = new OneToOneMapping<>();
        Integer first = 5;
        Integer second = 7;

        // act
        mapping.put(first, second);

        // assert
        assertEquals(second, mapping.getByFirst(first));
    }

    @Test
    void givenEmptyMapping_whenPutting5to7And5to9_thenGives9whenFirstIs5() {
        // arrange
        OneToOneMapping<Integer, Integer> mapping = new OneToOneMapping<>();
        Integer first = 5;
        Integer second = 7;
        Integer second2 = 9;

        // act
        mapping.put(first, second);
        mapping.put(first, second2);

        // assert
        assertEquals(second2, mapping.getByFirst(first));
    }

    @Test
    void givenEmptyMapping_whenPutting5to7And5to9_thenGives5whenSecondIs9() {
        // arrange
        OneToOneMapping<Integer, Integer> mapping = new OneToOneMapping<>();
        Integer first = 5;
        Integer second = 7;
        Integer second2 = 9;

        // act
        mapping.put(first, second);
        mapping.put(first, second2);

        // assert
        assertEquals(first, mapping.getBySecond(second2));
    }

    @Test
    void givenEmptyMapping_whenPutting5to7And5to9_thenGivesNullWhenSecondIs7() {
        // arrange
        OneToOneMapping<Integer, Integer> mapping = new OneToOneMapping<>();
        Integer first = 5;
        Integer second = 7;
        Integer second2 = 9;

        // act
        mapping.put(first, second);
        mapping.put(first, second2);

        // assert
        assertNull(mapping.getBySecond(second));
    }

    @Test
    void getByFirst() {
    }

    @Test
    void getBySecond() {
    }

    @Test
    void removeByFirst() {
    }

    @Test
    void removeBySecond() {
    }
}