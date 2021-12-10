# Search

**Prerequisite**: Select a [code system version branch](code-systems-and-branches.md) to search.

## Searching using a term parameter
The term based search uses **multiple prefix**, any order, matching. The first part of one or many words can be used to match the descriptions of concepts. There is no fuzzy 
matching, if a description term does not exactly match all the prefixes in the search term then it will not be included in the results.

When a term is provided in the search parameters then the primary sort order is the length of the term matched. This order has been chosen because most searches over SNOMED CT 
will return both an exact match concept and also many variations of that concept. The concept with the shortest matching term is likely to be simplest / most general concept. 

For example searching for "pneumonia" returns over 200 different results but the simplest concept `233604007 |Pneumonia (disorder)|` will be at the top. If the desired concept 
is not in the top few results then the search term could be refined; for example a search term of "pneum bac" will return `53084003 |Bacterial pneumonia (disorder)|` at the top.

## Limiting the scope of search
Snowstorm provides excellent API support for typeahead search UI components. When building a user interface that searches SNOMED CT it is recommended to limit the scope of the 
search to areas of the hierarchy relevant to that form field, for example only disorders or only procedures. 

This can be achieved with the concept based search by setting the `ecl` parameter, that uses the [Expression Constraint Language](http://snomed.org/ecl).

Alternatively when using the description based search the scope of the search can be limited by setting the `semanticTag` parameter.

## Search methods
### Concept based search
- **URL**  
  `GET /{branch}/concepts`


- **URL Params**   
  Recommended params: `?activeFilter=true&termActive=true` (Always set these to avoid matching inactive content).  
  Other available params include:`term`, `language`, `ecl`.


- **Response**  
The response includes some brief information about the concept including the active state, FSN (fully specified name), PT (preferred term), module, definition status, etc.  

The concept based search response does _not_ include the description that matched the term search. So a search of "heart att" will return the concept `22298006 |Myocardial infarction (disorder)|` but the response will not include the description "Heart attack", although that was used for matching and sorting.

### Description based search
- **URL**  
  `GET /browser/{branch}/descriptions`


- **URL Params**   
  Recommended params: `?active=true&conceptActive=true` (Always set these to avoid matching inactive content).  
  Limit search to a specific hierarchy using the `semanticTag` parameter using values such as "disorder", "finding", or "procedure".  
  Other available params include:`term`, `language`, `groupByConcept`.


- **Response**
    - This response includes several **aggregations** that provide summary information; 
    the number of matches in each module, semantic tag (hierarchy), language and 
      refset membership.
    - The **bucketConcepts** in the response provide further information about the modules and refsets quoted in the **aggregations** section.
    - Finally the **items** section lists the individual descriptions that matched the search, together with their term, language, and concept (with module, fsn, and pt).
