package org.snomed.snowstorm.config.elasticsearch;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.util.Date;

@ReadingConverter
public class LongToDateConverter implements Converter<Long, Date> {

	@Override
	public Date convert(Long dateInMillis) {
		return new Date(dateInMillis);
	}

}
