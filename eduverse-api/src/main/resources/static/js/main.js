document.addEventListener('DOMContentLoaded', () => {
    const registerForm = document.getElementById('registerForm');
    const statusDiv = document.getElementById('status');

    if (registerForm) {
        registerForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            
            const formData = new FormData(registerForm);
            const tenantId = formData.get('tenantId');
            const name = formData.get('name');

            statusDiv.style.display = 'block';
            statusDiv.innerHTML = '<p style="color: var(--primary);">Setting up your platform... Please wait.</p>';

            try {
                // We use URLSearchParams because the RegistrationController uses @RequestParam
                const params = new URLSearchParams();
                params.append('tenantId', tenantId);
                params.append('name', name);

                const response = await fetch('/api/register?' + params.toString(), {
                    method: 'POST'
                });

                if (response.ok) {
                    const data = await response.json();
                    statusDiv.innerHTML = `
                        <p style="color: #059669; font-weight: 600;">Success! Your school "${data.name}" is ready.</p>
                        <p style="margin-top: 1rem;">
                            Access it here: <a href="http://${data.tenantId}.eduverse.com:8080" style="color: var(--primary);">${data.tenantId}.eduverse.com</a>
                        </p>
                        <p style="font-size: 0.8rem; margin-top: 0.5rem; color: var(--secondary);">(Make sure you added this to your hosts file)</p>
                    `;
                } else {
                    const error = await response.text();
                    statusDiv.innerHTML = `<p style="color: #dc2626;">Error: ${error}</p>`;
                }
            } catch (err) {
                statusDiv.innerHTML = `<p style="color: #dc2626;">Failed to connect to server. Check if the app is running.</p>`;
            }
        });
    }
});
