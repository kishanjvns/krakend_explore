$content = @'
{
  "version": 3,
  "endpoints": [
    {
      "endpoint": "/api/users",
      "method": "GET",
      "backend": [
        {
          "url_pattern": "/users",
          "host": ["http://user-service:8000"]
        }
      ]
    },
    {
      "endpoint": "/api/products",
      "method": "GET",
      "backend": [
        {
          "url_pattern": "/products",
          "host": ["http://product-service:8000"]
        }
      ]
    }
  ]
}
'@

[IO.File]::WriteAllText("$PSScriptRoot\krakend.json", $content, (New-Object System.Text.UTF8Encoding $false))
Write-Host "krakend.json created successfully with UTF-8 encoding (no BOM)"
