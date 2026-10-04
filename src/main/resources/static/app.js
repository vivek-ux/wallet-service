const API_BASE = "/api/v1";
const tokenStorageKey = "walletServiceToken";

const elements = {
    accountForm: document.querySelector("#accountForm"),
    balanceValue: document.querySelector("#balanceValue"),
    historyButton: document.querySelector("#historyButton"),
    loginForm: document.querySelector("#loginForm"),
    logoutButton: document.querySelector("#logoutButton"),
    message: document.querySelector("#message"),
    refreshButton: document.querySelector("#refreshButton"),
    registerForm: document.querySelector("#registerForm"),
    sessionState: document.querySelector("#sessionState"),
    transactionRows: document.querySelector("#transactionRows"),
    transferForm: document.querySelector("#transferForm")
};

function getToken() {
    return localStorage.getItem(tokenStorageKey);
}

function setToken(token) {
    localStorage.setItem(tokenStorageKey, token);
    updateSessionState();
}

function clearToken() {
    localStorage.removeItem(tokenStorageKey);
    updateSessionState();
    if (elements.balanceValue) elements.balanceValue.textContent = "--";
    if (elements.transactionRows) {
        elements.transactionRows.innerHTML = '<tr><td colspan="6">No transactions loaded</td></tr>';
    }
}

function updateSessionState() {
    const token = getToken();
    if (elements.sessionState) {
        elements.sessionState.textContent = token ? "● Authenticated" : "Signed out";
    }
}

function showMessage(text, isError = false) {
    if (elements.message) {
        elements.message.textContent = text;
        elements.message.classList.toggle("error", isError);
    }
}

function formData(form) {
    return Object.fromEntries(new FormData(form).entries());
}

async function request(path, options = {}) {
    const fullPath = path.startsWith("/api/") ? path : `${API_BASE}${path.startsWith("/") ? "" : "/"}${path}`;
    
    const headers = {
        "Content-Type": "application/json",
        ...options.headers
    };

    const token = getToken();
    const isAuthRequest = fullPath.includes("/auth/");

    // Only attach Authorization header if we have a token AND it's NOT an auth endpoint
    if (token && !isAuthRequest) {
        headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(fullPath, {
        ...options,
        headers
    });

    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json")
        ? await response.json()
        : await response.text();

    if (!response.ok) {
        const message = typeof body === "string" ? body : body.message;

        if (response.status === 401 && !isAuthRequest) {
            clearToken();
            throw new Error("Your session expired. Please log in again.");
        }

        throw new Error(message || `Request failed with status ${response.status}`);
    }

    return body;
}

function money(value) {
    if (value === null || value === undefined || value === "") {
        return "--";
    }

    return Number(value).toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

function idempotencyKey() {
    if (crypto.randomUUID) {
        return crypto.randomUUID();
    }

    return `transfer-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function refreshBalance() {
    const balance = await request("/accounts/me/balance");
    if (elements.balanceValue) elements.balanceValue.textContent = money(balance);
}

async function refreshHistory() {
    const transactions = await request("/accounts/me/transactions");

    if (!elements.transactionRows) return;

    if (!Array.isArray(transactions) || transactions.length === 0) {
        elements.transactionRows.innerHTML = '<tr><td colspan="6">No transactions yet</td></tr>';
        return;
    }

    elements.transactionRows.innerHTML = transactions.map(transaction => `
        <tr>
            <td>${transaction.id}</td>
            <td>${transaction.fromEmail || "--"}</td>
            <td>${transaction.toEmail || "--"}</td>
            <td>${money(transaction.amount)}</td>
            <td>${transaction.status || "COMPLETED"}</td>
            <td>${transaction.createdAt ? new Date(transaction.createdAt).toLocaleString() : "--"}</td>
        </tr>
    `).join("");
}

async function refreshDashboard() {
    await Promise.all([
        refreshBalance(),
        refreshHistory()
    ]);
}

if (elements.registerForm) {
    elements.registerForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const button = form.querySelector("button");
        button.disabled = true;
        showMessage("Creating user...");

        try {
            const data = formData(form);
            const message = await request("/auth/register", {
                method: "POST",
                body: JSON.stringify(data)
            });
            showMessage(`${typeof message === 'string' ? message : 'User registered'}. Now log in with the same credentials.`);
            form.reset();
        } catch (error) {
            showMessage(error.message, true);
        } finally {
            button.disabled = false;
        }
    });
}

if (elements.loginForm) {
    elements.loginForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const button = form.querySelector("button");
        button.disabled = true;
        showMessage("Logging in...");

        try {
            const data = formData(form);
            const responseData = await request("/auth/login", {
                method: "POST",
                body: JSON.stringify(data)
            });
            
            // Extract token matching { "accessToken": "...", "refreshToken": "..." } payload
            const token = responseData?.accessToken || responseData?.token || responseData;

            if (!token || typeof token !== "string") {
                throw new Error("Invalid token received from server");
            }

            setToken(token);
            form.reset();
            
            try {
                await refreshDashboard();
                showMessage("Logged in");
            } catch (dashboardError) {
                if (dashboardError.message.toLowerCase().includes("account not found")) {
                    showMessage("Logged in. Create your wallet account below to continue.");
                } else {
                    showMessage(`Logged in, but dashboard refresh failed: ${dashboardError.message}`, true);
                }
            }
        } catch (error) {
            showMessage(error.message, true);
        } finally {
            button.disabled = false;
        }
    });
}

if (elements.accountForm) {
    elements.accountForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        showMessage("Creating account...");

        try {
            const data = formData(form);
            const message = await request("/accounts/create", {
                method: "POST",
                body: JSON.stringify({
                    initialBalance: data.initialBalance
                })
            });
            showMessage(typeof message === 'string' ? message : "Account created");
            form.reset();
            await refreshDashboard();
        } catch (error) {
            showMessage(error.message, true);
        }
    });
}

if (elements.transferForm) {
    elements.transferForm.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        showMessage("Sending transfer...");

        try {
            const data = formData(form);
            const message = await request("/accounts/transfer", {
                method: "POST",
                headers: {
                    "Idempotency-Key": data.idempotencyKey || idempotencyKey()
                },
                body: JSON.stringify({
                    toEmail: data.toEmail,
                    amount: data.amount
                })
            });
            showMessage(typeof message === 'string' ? message : "Transfer completed");
            form.reset();
            await refreshDashboard();
        } catch (error) {
            showMessage(error.message, true);
        }
    });
}

if (elements.refreshButton) {
    elements.refreshButton.addEventListener("click", async () => {
        try {
            await refreshDashboard();
            showMessage("Dashboard refreshed");
        } catch (error) {
            showMessage(error.message, true);
        }
    });
}

if (elements.historyButton) {
    elements.historyButton.addEventListener("click", async () => {
        try {
            await refreshHistory();
            showMessage("Transactions refreshed");
        } catch (error) {
            showMessage(error.message, true);
        }
    });
}

if (elements.logoutButton) {
    elements.logoutButton.addEventListener("click", () => {
        clearToken();
        showMessage("Logged out");
    });
}

updateSessionState();

if (getToken()) {
    refreshDashboard().catch(error => showMessage(error.message, true));
}