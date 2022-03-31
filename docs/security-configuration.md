# Snowstorm Security Configuration Guide

## Read Only Mode
In many cases Snowstorm can be run in read-only mode. Simply load SNOMED CT data and then switch on read-only mode using configuration option `snowstorm.rest-api.readonly=false`.
In this mode all API functions which make changes to content are disabled, they are also hiden from the Swagger API documentation page.

## Role Based Access Control
The alternative approach is to enable role based access to the API. In this mode groups of users can be granted the `ADMIN` or `AUTHOR` role either globaly or on specific branches.
If a role is granted on a branch that role will also be granted on ancestor branches but not if that ancestor branch contains a different code system.

The `ADMIN` role is required for functions with `/admin/` in the URL and changing code systems, updating the metadata and lock status of branches and reloading authoring validation rules.

The `AUTHOR` roles is required for making content changes, for example creating or updating concepts or reference set members or importing RF2.

Roles can be granted to user groups using the Admin Permissions section of the API, see [Swagger docs](http://localhost:8080/).

RBAC is not enabled per default. To enable it use the configuration option `ims-security.roles.enabled=true`.

### Checking Which Roles Apply
The roles of the currently logged in user are listed against each branch, for example if the current user is in a group which allows authoring on the US extension:  
`GET /branches/MAIN/SNOMEDCT-US`  would return:
```json
{
    "path": "MAIN/SNOMEDCT-US",
    ...
    "userRoles": [
        "AUTHOR"
    ],
    ...
}
```

### User Identification Setup
This feature relies on the following HTTP headers being supplied by NGinx: `X-AUTH-username`, `X-AUTH-roles`, `X-AUTH-token`. 
The IHTSDO Identification Management Service can be used which integrates with Atlassian Jira and Crowd. In theory any other authentication provider could be used if these HTTP headers are set per user.
