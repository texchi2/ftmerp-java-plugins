<#if user??>
<div style="padding: 8px 0;">
    <a href="javascript:void(0)"
       style="color: #cc0000; text-decoration: underline; cursor: pointer;"
       onclick="if(confirm('Permanently delete user [${user.employeeId}] ${user.fullName}? This cannot be undone.')) {
           window.location='/ftm-wifi/control/DeleteUser?employeeId=${user.employeeId}&externalLoginKey=${requestAttributes.externalLoginKey!}';
       }">
        Delete User permanently
    </a>
</div>
</#if>
