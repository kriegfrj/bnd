package aQute.junit.platform.test;

import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class JUnit5ParameterizedTest {

	@ParameterizedTest(name = "{index} ==> param: ''{0}'', param2: {1}")
	@MethodSource("provideArgs")
	public void parameterizedMethod(String param, float param2) {
		Assertions.assertThat(param)
			.isNotEqualTo("four");
	}

	public static Stream<Arguments> provideArgs() {
		return Stream.of(Arguments.of("one", 1.0f), Arguments.of("two", 2.0f), Arguments.of("three", 3.0f),
			Arguments.of("four", 4.0f), Arguments.of("five", 5.0f));
	}

	@ParameterizedTest
	@MethodSource("unknownMethod")
	public void misconfiguredMethod(String param) {
	}
}