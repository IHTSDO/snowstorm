package org.snomed.snowstorm.core.data.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DescriptionTest {

	@Test
	void getTag() {
		assertNull(new Description("abc de").setTypeId(Concepts.SYNONYM).getTag());
		assertNull(new Description("abc de (disorder)").setTypeId(Concepts.SYNONYM).getTag());
		assertNull(new Description("abc de (disorder) ").setTypeId(Concepts.FSN).getTag());
		assertNull(new Description("abc de (disorder").setTypeId(Concepts.FSN).getTag());
		assertNull(new Description("abc de (thing) 1").setTypeId(Concepts.FSN).getTag());
		assertNull(new Description("abc de(disorder)").setTypeId(Concepts.FSN).getTag());
		assertNull(new Description("abc de(disorder").setTypeId(Concepts.FSN).getTag());
		assertEquals("disorder", new Description("abc de (disorder)").setTypeId(Concepts.FSN).getTag());
		assertEquals("disorder aa", new Description("abc de (disorder aa)").setTypeId(Concepts.FSN).getTag());
	}

}
