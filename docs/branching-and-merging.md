# Version Control

Version control in Snowstorm allows us to record different versions of content through time and to use branches to organise that content.

## Branches

Branches allow for segregation of terminology content. They can be used to develop multiple content projects in parallel, to hold multiple release versions of SNOMED CT 
or even to hold many versions of many extensions in a single terminology server. 

Branches have a hierarchical structure. The root branch is called MAIN. The MAIN branch may have many children and those children may have many children. 
The breadth and depth of branching is not limited.

Example branch hierarchy:
```
MAIN
  - 2019-01-31
  - 2018-07-31
  - ProjectA
    - Task1
    - Task2
  - ProjectB
    - Task1
    - Task2
```

## Timeline
Each branch has a timeline. It starts when the branch is created. Every time content is changed on a branch a new version of the branch is created. 
The content of a branch can change in many ways including RF2 importing, content authoring or branch merge operations.

Every branch has a property called _**head**_, this is the date when the latest version of that branch was created.

## Branch contents
When a branch is created all content which exists on the specified parent branch at that point in time is visible on the child branch. 
The content of the parent is not copied to the child branch. 

Every branch has a property called _**base**_ timestamp.  

```

```

The content on each branch is accessed using the branch path.