#Requires -RunAsAdministrator
[CmdletBinding()]
param([switch]$Remove)

$ErrorActionPreference = 'Stop'
$hostsPath = Join-Path ([Environment]::GetFolderPath('System')) 'drivers\etc\hosts'
$domains = @('ai-erp.duckdns.org', 'blackcow.duckdns.org')
$start = '# BEGIN AI ERP LOCAL DNS'
$end = '# END AI ERP LOCAL DNS'
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$bytes = [IO.File]::ReadAllBytes($hostsPath)
$hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191
$offset = if ($hasBom) { 3 } else { 0 }
$content = $utf8.GetString($bytes, $offset, $bytes.Length - $offset)
$newline = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
$block = @($start, '192.168.219.100 ai-erp.duckdns.org blackcow.duckdns.org', $end) -join $newline
$hasMarker = $content.Contains($start) -or $content.Contains($end)
$blockMatches = [regex]::Matches($content, '(?m)^' + [regex]::Escape($block) + '(?:\r?\n|\z)')
$markerCount = [regex]::Matches($content, [regex]::Escape($start) + '|' + [regex]::Escape($end)).Count
if ($hasMarker -and ($blockMatches.Count -ne 1 -or $markerCount -ne 2)) {
    throw 'The AI ERP hosts block was edited. Inspect it before updating.'
}
if ($blockMatches.Count -eq 1) {
    if (-not $Remove) { Write-Output 'AI ERP local domain mapping is already configured.'; return }
    $managedBlock = $blockMatches[0]
    $updated = $content.Remove($managedBlock.Index, $managedBlock.Length)
} else {
    if ($Remove) { Write-Output 'No managed AI ERP hosts block is present.'; return }
    foreach ($line in ($content -split '\r?\n')) {
        $tokens = @(($line -split '#', 2)[0].Trim() -split '\s+')
        foreach ($domain in $domains) {
            if ($tokens -contains $domain) {
                throw "An existing hosts entry for $domain must be reviewed first."
            }
        }
    }
    $separator = if ($content.Length -gt 0 -and -not $content.EndsWith("`n")) { $newline } else { '' }
    $updated = $content + $separator + $block + $newline
}

# Keep the original beside hosts without altering its existing access control.
$backupPath = $hostsPath + '.ai-erp-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffffff') + '.bak'
Copy-Item -LiteralPath $hostsPath -Destination $backupPath -ErrorAction Stop
$newBytes = $utf8.GetBytes($updated)
if ($hasBom) { $newBytes = [byte[]](@(239, 187, 191) + $newBytes) }
[IO.File]::WriteAllBytes($hostsPath, $newBytes)
Clear-DnsClientCache
Write-Output "AI ERP local domain mapping updated. Backup: $backupPath"
Write-Output 'Use -Remove to remove only the managed AI ERP entries.'
