/**
 * ExpenseFlow v2.0 - Frontend Client Application
 * Multi-tenant JWT Authentication, Multi-Currency, Custom Categories & Chart.js
 */

const API_BASE = '/api/transactions';
const BUDGET_API = '/api/budget';
const AUTH_API = '/api/auth';
const CATEGORY_API = '/api/categories';

let authToken = localStorage.getItem('expense_token') || null;
let currentUser = JSON.parse(localStorage.getItem('expense_user') || 'null');
let userCurrency = (currentUser && currentUser.currency) || 'INR';

let currentPage = 0;
const pageSize = 10;
let totalPages = 0;

let monthlyChart = null;
let categoryChart = null;
let isRegisterMode = false;

// Currency mapping
const CURRENCY_CONFIG = {
    INR: { locale: 'en-IN', currency: 'INR', symbol: '₹' },
    USD: { locale: 'en-US', currency: 'USD', symbol: '$' },
    EUR: { locale: 'de-DE', currency: 'EUR', symbol: '€' },
    GBP: { locale: 'en-GB', currency: 'GBP', symbol: '£' }
};

// Initialize application
document.addEventListener('DOMContentLoaded', () => {
    handleOAuthRedirect();
    initDefaultDates();
    setupEventListeners();

    if (authToken && currentUser) {
        setLoggedInUI(true);
        loadDashboard();
    } else {
        setLoggedInUI(false);
    }
});

/**
 * Handle Google OAuth2 Redirect Callback
 */
function handleOAuthRedirect() {
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');
    const error = urlParams.get('error');

    if (error) {
        showToast('Login Failed', decodeURIComponent(error), 'error');
        window.history.replaceState({}, document.title, window.location.pathname);
        return;
    }

    if (token) {
        authToken = token;
        currentUser = {
            id: Number(urlParams.get('userId')),
            name: decodeURIComponent(urlParams.get('name') || 'Google User'),
            email: decodeURIComponent(urlParams.get('email') || ''),
            currency: urlParams.get('currency') || 'INR'
        };
        userCurrency = currentUser.currency;

        localStorage.setItem('expense_token', authToken);
        localStorage.setItem('expense_user', JSON.stringify(currentUser));

        // Clean query parameters from URL bar
        window.history.replaceState({}, document.title, window.location.pathname);

        showToast('Signed In', `Welcome ${currentUser.name}!`, 'success');
    }
}

function initDefaultDates() {
    const today = new Date();
    const curYear = today.getFullYear();
    const curMonth = String(today.getMonth() + 1).padStart(2, '0');
    const curDay = String(today.getDate()).padStart(2, '0');

    const txDateInput = document.getElementById('txDate');
    if (txDateInput) txDateInput.value = `${curYear}-${curMonth}-${curDay}`;

    const budgetMonthInput = document.getElementById('budgetMonth');
    if (budgetMonthInput) budgetMonthInput.value = `${curYear}-${curMonth}`;
}

function setupEventListeners() {
    let searchDebounceTimer;
    const searchInput = document.getElementById('filterSearch');
    if (searchInput) {
        searchInput.addEventListener('input', () => {
            clearTimeout(searchDebounceTimer);
            searchDebounceTimer = setTimeout(() => {
                currentPage = 0;
                fetchTransactions();
            }, 350);
        });
    }

    ['filterType', 'filterCategory', 'filterStartDate', 'filterEndDate'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.addEventListener('change', () => {
                currentPage = 0;
                loadDashboard();
            });
        }
    });
}

function updateCurrencySymbols() {
    const symbol = (CURRENCY_CONFIG[userCurrency] || CURRENCY_CONFIG.INR).symbol;
    document.querySelectorAll('.currencySymbol').forEach(el => {
        el.textContent = symbol;
    });
    const currencySelector = document.getElementById('userCurrencySelector');
    if (currencySelector) {
        currencySelector.value = userCurrency;
    }
}

/**
 * Authorized Fetch Wrapper
 */
async function authFetch(url, options = {}) {
    options.headers = options.headers || {};
    if (authToken) {
        options.headers['Authorization'] = `Bearer ${authToken}`;
    }

    const response = await fetch(url, options);

    if (response.status === 401 || response.status === 403) {
        logout(false);
        showAuthModal(false);
        throw new Error('Session expired. Please log in again.');
    }

    return response;
}

/**
 * UI Visibility Toggle for Auth State
 */
function setLoggedInUI(isLoggedIn) {
    const authHero = document.getElementById('authHeroSection');
    const dashboardMain = document.getElementById('dashboardMainSection');
    const userProfileNav = document.getElementById('userProfileNav');
    const currencyContainer = document.getElementById('currencySelectorContainer');
    const loginPromptBtn = document.getElementById('loginPromptBtn');
    const categoryBtn = document.getElementById('categoryManagerBtn');
    const budgetBtn = document.getElementById('budgetSettingsBtn');
    const newTxBtn = document.getElementById('newTxBtn');

    if (isLoggedIn) {
        authHero.classList.add('hidden');
        dashboardMain.classList.remove('hidden');
        userProfileNav.classList.remove('hidden');
        userProfileNav.classList.add('flex');
        currencyContainer.classList.remove('hidden');
        currencyContainer.classList.add('flex');
        loginPromptBtn.classList.add('hidden');
        categoryBtn.classList.remove('hidden');
        budgetBtn.classList.remove('hidden');
        newTxBtn.classList.remove('hidden');

        document.getElementById('navUserName').textContent = currentUser ? currentUser.name : 'User';
        userCurrency = (currentUser && currentUser.currency) || 'INR';
        updateCurrencySymbols();
        loadCategories();
    } else {
        authHero.classList.remove('hidden');
        dashboardMain.classList.add('hidden');
        userProfileNav.classList.add('hidden');
        currencyContainer.classList.add('hidden');
        loginPromptBtn.classList.remove('hidden');
        categoryBtn.classList.add('hidden');
        budgetBtn.classList.add('hidden');
        newTxBtn.classList.add('hidden');
    }
}

/**
 * Category Management
 */
async function loadCategories() {
    try {
        const response = await authFetch(CATEGORY_API);
        if (!response.ok) return;
        const categories = await response.json();

        // Populate Transaction Modal dropdown
        const txCategory = document.getElementById('txCategory');
        const filterCategory = document.getElementById('filterCategory');

        const currentFilterVal = filterCategory.value;
        const currentTxVal = txCategory.value;

        filterCategory.innerHTML = '<option value="">All Categories</option>';
        txCategory.innerHTML = '';

        categories.forEach(c => {
            const opt1 = document.createElement('option');
            opt1.value = c.name;
            opt1.textContent = c.name;
            filterCategory.appendChild(opt1);

            const opt2 = document.createElement('option');
            opt2.value = c.name;
            opt2.textContent = c.name;
            txCategory.appendChild(opt2);
        });

        if (currentFilterVal) filterCategory.value = currentFilterVal;
        if (currentTxVal) txCategory.value = currentTxVal;

        // Populate Category Manager Modal List
        const modalList = document.getElementById('categoryModalList');
        if (modalList) {
            modalList.innerHTML = categories.map(c => `
                <div class="flex items-center justify-between p-2.5">
                    <div class="flex items-center space-x-2">
                        <span class="w-3 h-3 rounded-full" style="background-color: ${c.color}"></span>
                        <span class="font-medium text-slate-800">${escapeHtml(c.name)}</span>
                        <span class="text-[10px] uppercase font-bold px-1.5 py-0.5 rounded ${c.type === 'EXPENSE' ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'}">${c.type}</span>
                    </div>
                    ${c.custom ? `
                        <button onclick="deleteCustomCategory(${c.id})" class="text-slate-400 hover:text-rose-600 p-1">
                            <i class="fa-regular fa-trash-can"></i>
                        </button>
                    ` : '<span class="text-[10px] text-slate-400 font-semibold">Default</span>'}
                </div>
            `).join('');
        }

    } catch (err) {
        console.error('Failed to load categories:', err);
    }
}

function openCategoryModal() {
    loadCategories();
    document.getElementById('categoryModal').classList.remove('hidden');
}

function closeCategoryModal() {
    document.getElementById('categoryModal').classList.add('hidden');
}

async function handleCreateCategory(event) {
    event.preventDefault();
    const name = document.getElementById('newCategoryName').value.trim();
    const type = document.getElementById('newCategoryType').value;

    try {
        const response = await authFetch(CATEGORY_API, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, type })
        });

        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.message || 'Failed to add category');
        }

        document.getElementById('newCategoryName').value = '';
        showToast('Created', `Category '${name}' created!`, 'success');
        loadCategories();
    } catch (err) {
        showToast('Error', err.message, 'error');
    }
}

async function deleteCustomCategory(id) {
    if (!confirm('Are you sure you want to delete this custom category?')) return;
    try {
        const response = await authFetch(`${CATEGORY_API}/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Cannot delete category');
        showToast('Deleted', 'Category removed.', 'info');
        loadCategories();
    } catch (err) {
        showToast('Error', err.message, 'error');
    }
}

/**
 * Currency Switcher
 */
async function changeCurrency(newCurrency) {
    userCurrency = newCurrency;
    updateCurrencySymbols();

    if (currentUser) {
        currentUser.currency = newCurrency;
        localStorage.setItem('expense_user', JSON.stringify(currentUser));

        try {
            await authFetch(`${AUTH_API}/profile`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: currentUser.name, currency: newCurrency })
            });
            showToast('Currency Updated', `Display currency changed to ${newCurrency}.`, 'success');
        } catch (e) {
            console.error(e);
        }
    }

    loadDashboard();
}

/**
 * Filter Parameters
 */
function getFilterParams() {
    const search = document.getElementById('filterSearch').value.trim();
    const type = document.getElementById('filterType').value;
    const category = document.getElementById('filterCategory').value;
    const startDate = document.getElementById('filterStartDate').value;
    const endDate = document.getElementById('filterEndDate').value;

    const params = new URLSearchParams();
    if (search) params.append('search', search);
    if (type) params.append('type', type);
    if (category) params.append('category', category);
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);

    return params;
}

async function loadDashboard() {
    if (!authToken) return;
    await Promise.all([
        fetchSummary(),
        fetchTransactions()
    ]);
}

/**
 * Summary & Charts
 */
async function fetchSummary() {
    try {
        const filterParams = getFilterParams();
        const response = await authFetch(`${API_BASE}/summary?${filterParams.toString()}`);
        if (!response.ok) throw new Error('Failed to load summary');
        const data = await response.json();

        // 1. KPI Cards
        document.getElementById('kpiTotalIncome').textContent = formatCurrency(data.totalIncome || 0);
        document.getElementById('kpiTotalExpense').textContent = formatCurrency(data.totalExpense || 0);

        const balanceEl = document.getElementById('kpiBalance');
        const balance = data.balance || 0;
        balanceEl.textContent = formatCurrency(balance);
        balanceEl.className = `text-2xl font-bold tracking-tight ${balance >= 0 ? 'text-slate-900' : 'text-rose-600'}`;

        document.getElementById('kpiTopCategory').textContent = data.topSpendingCategory || 'None';

        // 2. Budget Health Card
        const budgetLimit = data.monthlyBudgetLimit || 0;
        const usedPercent = data.budgetUsedPercentage || 0;
        const budgetBar = document.getElementById('kpiBudgetBar');

        document.getElementById('kpiBudgetLabel').textContent = `Budget: ${formatCurrency(budgetLimit)}`;
        document.getElementById('kpiBudgetPercent').textContent = `${usedPercent}%`;
        budgetBar.style.width = `${Math.min(usedPercent, 100)}%`;

        if (usedPercent >= 100) {
            budgetBar.className = 'bg-rose-500 h-2 rounded-full transition-all duration-500';
        } else if (usedPercent >= 80) {
            budgetBar.className = 'bg-amber-500 h-2 rounded-full transition-all duration-500';
        } else {
            budgetBar.className = 'bg-emerald-500 h-2 rounded-full transition-all duration-500';
        }

        // 3. Dynamic Alert Banner
        renderBudgetAlert(data);

        // 4. Update Charts
        renderMonthlyTrendChart(data.monthlyTrends || []);
        renderCategoryChart(data.categoryExpenses || {});

    } catch (err) {
        console.error('Error fetching summary:', err);
    }
}

function renderBudgetAlert(summary) {
    const banner = document.getElementById('budgetAlertBanner');
    const icon = document.getElementById('budgetAlertIcon');
    const title = document.getElementById('budgetAlertTitle');
    const text = document.getElementById('budgetAlertText');

    if (summary.alertStatus === 'EXCEEDED') {
        banner.className = 'block rounded-xl p-4 bg-rose-50 border border-rose-200 text-rose-900 animate-fade-in';
        icon.className = 'p-2 rounded-lg bg-rose-100 text-rose-600';
        icon.innerHTML = '<i class="fa-solid fa-triangle-exclamation text-lg"></i>';
        title.textContent = 'Budget Limit Exceeded!';
        text.textContent = summary.alertMessage;
    } else if (summary.alertStatus === 'WARNING') {
        banner.className = 'block rounded-xl p-4 bg-amber-50 border border-amber-200 text-amber-900 animate-fade-in';
        icon.className = 'p-2 rounded-lg bg-amber-100 text-amber-600';
        icon.innerHTML = '<i class="fa-solid fa-bell text-lg"></i>';
        title.textContent = 'Budget Warning (Above 80%)';
        text.textContent = summary.alertMessage;
    } else {
        banner.className = 'hidden';
    }
}

/**
 * Transactions Table & Pagination
 */
async function fetchTransactions() {
    const tableBody = document.getElementById('transactionsTableBody');
    try {
        const params = getFilterParams();
        params.append('page', currentPage);
        params.append('size', pageSize);
        params.append('sortBy', 'transactionDate');
        params.append('direction', 'DESC');

        const response = await authFetch(`${API_BASE}?${params.toString()}`);
        if (!response.ok) throw new Error('Failed to load transactions');
        const data = await response.json();

        totalPages = data.totalPages;

        if (!data.content || data.content.length === 0) {
            tableBody.innerHTML = `
                <tr>
                    <td colspan="7" class="py-12 text-center">
                        <div class="flex flex-col items-center justify-center text-slate-400">
                            <i class="fa-regular fa-folder-open text-4xl mb-3 text-slate-300"></i>
                            <p class="text-sm font-semibold text-slate-600">No transactions found</p>
                            <p class="text-xs text-slate-400 mt-1">Try clearing filters or record a new transaction.</p>
                        </div>
                    </td>
                </tr>`;
        } else {
            tableBody.innerHTML = data.content.map(tx => {
                const isExpense = tx.type === 'EXPENSE';
                const formattedAmount = `${isExpense ? '-' : '+'}${formatCurrency(tx.amount)}`;
                const amountClass = isExpense ? 'text-rose-600' : 'text-emerald-600 font-bold';
                const badgeTypeClass = isExpense ? 'bg-rose-50 text-rose-700 border-rose-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200';

                return `
                    <tr class="hover:bg-slate-50/70 border-b border-slate-100 transition-colors">
                        <td class="py-3.5 pl-6 pr-3 whitespace-nowrap text-slate-500 text-xs font-mono">
                            ${tx.transactionDate}
                        </td>
                        <td class="px-3 py-3.5 text-slate-900 font-semibold max-w-[200px] truncate" title="${escapeHtml(tx.title)}">
                            ${escapeHtml(tx.title)}
                        </td>
                        <td class="px-3 py-3.5 whitespace-nowrap">
                            <span class="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">
                                ${escapeHtml(tx.category)}
                            </span>
                        </td>
                        <td class="px-3 py-3.5 whitespace-nowrap">
                            <span class="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border ${badgeTypeClass}">
                                ${tx.type}
                            </span>
                        </td>
                        <td class="px-3 py-3.5 whitespace-nowrap font-mono text-sm ${amountClass}">
                            ${formattedAmount}
                        </td>
                        <td class="px-3 py-3.5 text-slate-500 text-xs max-w-[220px] truncate" title="${escapeHtml(tx.notes || '')}">
                            ${escapeHtml(tx.notes || '-')}
                        </td>
                        <td class="py-3.5 pl-3 pr-6 text-right whitespace-nowrap space-x-2">
                            <button onclick="editTransaction(${tx.id})" title="Edit" class="text-slate-400 hover:text-emerald-600 p-1 transition-colors">
                                <i class="fa-regular fa-pen-to-square"></i>
                            </button>
                            <button onclick="deleteTransaction(${tx.id})" title="Delete" class="text-slate-400 hover:text-rose-600 p-1 transition-colors">
                                <i class="fa-regular fa-trash-can"></i>
                            </button>
                        </td>
                    </tr>
                `;
            }).join('');
        }

        // Render Pagination Info & Direct Page Numbers
        const totalElements = data.totalElements || 0;
        const start = totalElements === 0 ? 0 : currentPage * pageSize + 1;
        const end = Math.min((currentPage + 1) * pageSize, totalElements);
        document.getElementById('paginationInfo').textContent = `Showing ${start} to ${end} of ${totalElements} entries`;

        renderPaginationControls();

    } catch (err) {
        console.error('Error fetching transactions:', err);
        tableBody.innerHTML = `<tr><td colspan="7" class="py-6 text-center text-rose-500 font-semibold text-xs">${err.message}</td></tr>`;
    }
}

function renderPaginationControls() {
    const container = document.getElementById('paginationButtonsContainer');
    if (!container) return;

    if (totalPages <= 1) {
        container.innerHTML = '';
        return;
    }

    let html = `
        <button onclick="goToPage(${currentPage - 1})" ${currentPage === 0 ? 'disabled' : ''}
                class="px-2.5 py-1 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-lg hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed">
            <i class="fa-solid fa-chevron-left"></i>
        </button>
    `;

    for (let p = 0; p < totalPages; p++) {
        const isCurrent = p === currentPage;
        html += `
            <button onclick="goToPage(${p})"
                    class="px-3 py-1 text-xs font-bold rounded-lg ${isCurrent ? 'bg-emerald-600 text-white shadow-xs' : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-100'}">
                ${p + 1}
            </button>
        `;
    }

    html += `
        <button onclick="goToPage(${currentPage + 1})" ${currentPage >= totalPages - 1 ? 'disabled' : ''}
                class="px-2.5 py-1 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-lg hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed">
            <i class="fa-solid fa-chevron-right"></i>
        </button>
    `;

    container.innerHTML = html;
}

function goToPage(page) {
    if (page >= 0 && page < totalPages) {
        currentPage = page;
        fetchTransactions();
    }
}

function resetFilters() {
    document.getElementById('filterSearch').value = '';
    document.getElementById('filterType').value = '';
    document.getElementById('filterCategory').value = '';
    document.getElementById('filterStartDate').value = '';
    document.getElementById('filterEndDate').value = '';
    currentPage = 0;
    loadDashboard();
}

/**
 * Chart.js Visualizations
 */
function renderMonthlyTrendChart(trends) {
    const canvas = document.getElementById('monthlyTrendChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    const labels = trends.map(t => t.month);
    const incomes = trends.map(t => t.income);
    const expenses = trends.map(t => t.expense);

    if (monthlyChart) monthlyChart.destroy();

    monthlyChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Income',
                    data: incomes,
                    backgroundColor: 'rgba(34, 197, 94, 0.85)',
                    borderRadius: 6
                },
                {
                    label: 'Expense',
                    data: expenses,
                    backgroundColor: 'rgba(244, 63, 94, 0.85)',
                    borderRadius: 6
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: (ctx) => ` ${ctx.dataset.label}: ${formatCurrency(ctx.raw)}`
                    }
                }
            },
            scales: {
                x: { grid: { display: false } },
                y: {
                    beginAtZero: true,
                    ticks: {
                        callback: (value) => formatCurrency(value)
                    },
                    grid: { color: 'rgba(226, 232, 240, 0.6)' }
                }
            }
        }
    });
}

function renderCategoryChart(categoryMap) {
    const canvas = document.getElementById('categoryChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    const labels = Object.keys(categoryMap);
    const data = Object.values(categoryMap);

    if (categoryChart) categoryChart.destroy();

    if (labels.length === 0) {
        categoryChart = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: ['No Expenses'],
                datasets: [{ data: [1], backgroundColor: ['#e2e8f0'] }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { enabled: false }
                }
            }
        });
        return;
    }

    const palette = ['#f59e0b', '#6366f1', '#ec4899', '#10b981', '#8b5cf6', '#06b6d4', '#f97316', '#64748b'];

    categoryChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: data,
                backgroundColor: palette.slice(0, labels.length),
                borderWidth: 2,
                borderColor: '#ffffff'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '70%',
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: { boxWidth: 10, font: { size: 11 }, padding: 12 }
                },
                tooltip: {
                    callbacks: {
                        label: (ctx) => ` ${ctx.label}: ${formatCurrency(ctx.raw)}`
                    }
                }
            }
        }
    });
}

/**
 * Transaction Modal
 */
function openTransactionModal(editMode = false) {
    document.getElementById('modalError').classList.add('hidden');
    document.getElementById('modalTitle').textContent = editMode ? 'Edit Transaction' : 'Add Transaction';
    document.getElementById('modalSubmitBtn').textContent = editMode ? 'Update' : 'Save Transaction';

    if (!editMode) {
        document.getElementById('txForm').reset();
        document.getElementById('txId').value = '';
        initDefaultDates();
    }

    document.getElementById('txModal').classList.remove('hidden');
}

function closeTransactionModal() {
    document.getElementById('txModal').classList.add('hidden');
}

async function editTransaction(id) {
    try {
        const response = await authFetch(`${API_BASE}/${id}`);
        if (!response.ok) throw new Error('Failed to retrieve transaction');
        const tx = await response.json();

        document.getElementById('txId').value = tx.id;
        document.getElementById('txTitle').value = tx.title;
        document.getElementById('txAmount').value = tx.amount;
        document.getElementById('txType').value = tx.type;
        document.getElementById('txCategory').value = tx.category;
        document.getElementById('txDate').value = tx.transactionDate;
        document.getElementById('txNotes').value = tx.notes || '';

        openTransactionModal(true);
    } catch (err) {
        showToast('Error', err.message, 'error');
    }
}

async function handleTransactionSubmit(event) {
    event.preventDefault();
    const id = document.getElementById('txId').value;
    const isEdit = Boolean(id);

    const payload = {
        title: document.getElementById('txTitle').value.trim(),
        amount: parseFloat(document.getElementById('txAmount').value),
        type: document.getElementById('txType').value,
        category: document.getElementById('txCategory').value,
        transactionDate: document.getElementById('txDate').value,
        notes: document.getElementById('txNotes').value.trim()
    };

    const url = isEdit ? `${API_BASE}/${id}` : API_BASE;
    const method = isEdit ? 'PUT' : 'POST';

    try {
        const response = await authFetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const errData = await response.json();
            const msg = errData.message || (errData.errors ? Object.values(errData.errors).join(', ') : 'Failed to save');
            throw new Error(msg);
        }

        closeTransactionModal();
        showToast('Success', `Transaction ${isEdit ? 'updated' : 'created'} successfully!`, 'success');
        loadDashboard();

    } catch (err) {
        const errEl = document.getElementById('modalError');
        errEl.textContent = err.message;
        errEl.classList.remove('hidden');
    }
}

async function deleteTransaction(id) {
    if (!confirm('Are you sure you want to permanently delete this transaction?')) return;

    try {
        const response = await authFetch(`${API_BASE}/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Failed to delete transaction');

        showToast('Deleted', 'Transaction removed.', 'info');
        loadDashboard();
    } catch (err) {
        showToast('Error', err.message, 'error');
    }
}

/**
 * Budget Modal
 */
async function openBudgetModal() {
    const today = new Date();
    const curMonth = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`;
    const budgetMonthInput = document.getElementById('budgetMonth');
    budgetMonthInput.value = curMonth;

    try {
        const response = await authFetch(`${BUDGET_API}?monthYear=${curMonth}`);
        if (response.ok) {
            const data = await response.json();
            document.getElementById('budgetLimit').value = data.monthlyLimit || '';
        }
    } catch (err) {
        console.error(err);
    }

    document.getElementById('budgetModal').classList.remove('hidden');
}

function closeBudgetModal() {
    document.getElementById('budgetModal').classList.add('hidden');
}

async function handleBudgetSubmit(event) {
    event.preventDefault();
    const payload = {
        monthYear: document.getElementById('budgetMonth').value,
        monthlyLimit: parseFloat(document.getElementById('budgetLimit').value)
    };

    try {
        const response = await authFetch(BUDGET_API, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) throw new Error('Failed to save monthly budget');

        closeBudgetModal();
        showToast('Budget Saved', `Monthly budget updated to ${formatCurrency(payload.monthlyLimit)}.`, 'success');
        loadDashboard();
    } catch (err) {
        showToast('Error', err.message, 'error');
    }
}

/**
 * CSV Data Export
 */
async function exportToCSV() {
    const params = getFilterParams();
    try {
        const response = await authFetch(`${API_BASE}/export/csv?${params.toString()}`);
        if (!response.ok) throw new Error('Failed to export CSV');

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `expenses_export_${new Date().toISOString().slice(0, 10)}.csv`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        showToast('Export Completed', 'CSV file downloaded.', 'info');
    } catch (e) {
        showToast('Export Error', e.message, 'error');
    }
}

/**
 * Authentication Handling
 */
function showAuthModal(register = false) {
    isRegisterMode = register;
    document.getElementById('authError').classList.add('hidden');

    const title = document.getElementById('authModalTitle');
    const subtitle = document.getElementById('authModalSubtitle');
    const submitBtn = document.getElementById('authSubmitBtn');
    const toggleBtn = document.getElementById('authToggleBtn');
    const nameField = document.getElementById('authNameField');
    const currencyField = document.getElementById('authCurrencyField');

    if (isRegisterMode) {
        title.textContent = 'Create Account';
        subtitle.textContent = 'Get started with private personal expense tracking';
        submitBtn.textContent = 'Register Account';
        toggleBtn.textContent = 'Already have an account? Sign in';
        nameField.classList.remove('hidden');
        currencyField.classList.remove('hidden');
    } else {
        title.textContent = 'Sign In';
        subtitle.textContent = 'Access your private financial dashboard';
        submitBtn.textContent = 'Sign In';
        toggleBtn.textContent = "Don't have an account? Sign up";
        nameField.classList.add('hidden');
        currencyField.classList.add('hidden');
    }

    document.getElementById('authModal').classList.remove('hidden');
}

function closeAuthModal() {
    document.getElementById('authModal').classList.add('hidden');
}

function toggleAuthMode() {
    showAuthModal(!isRegisterMode);
}

function fillDemoCredentials() {
    showAuthModal(false);
    document.getElementById('authEmail').value = 'demo@example.com';
    document.getElementById('authPassword').value = 'Password123';
}

async function handleAuthSubmit(event) {
    event.preventDefault();
    const email = document.getElementById('authEmail').value.trim();
    const password = document.getElementById('authPassword').value;

    const endpoint = isRegisterMode ? `${AUTH_API}/register` : `${AUTH_API}/login`;
    const payload = isRegisterMode ? {
        name: document.getElementById('authName').value.trim(),
        email,
        password,
        currency: document.getElementById('authCurrency').value
    } : { email, password };

    try {
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Authentication failed');
        }

        authToken = data.token;
        currentUser = {
            id: data.userId,
            name: data.name,
            email: data.email,
            currency: data.currency || 'INR'
        };

        localStorage.setItem('expense_token', authToken);
        localStorage.setItem('expense_user', JSON.stringify(currentUser));

        closeAuthModal();
        setLoggedInUI(true);
        loadDashboard();
        showToast('Welcome', `Signed in as ${currentUser.name}!`, 'success');

    } catch (err) {
        const errEl = document.getElementById('authError');
        errEl.textContent = err.message;
        errEl.classList.remove('hidden');
    }
}

function logout(manual = true) {
    authToken = null;
    currentUser = null;
    localStorage.removeItem('expense_token');
    localStorage.removeItem('expense_user');
    setLoggedInUI(false);
    if (manual) showToast('Signed Out', 'You have been logged out.', 'info');
}

/**
 * Toast Notifications
 */
function showToast(title, message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');

    const colorClasses = {
        success: 'bg-emerald-600 text-white',
        error: 'bg-rose-600 text-white',
        info: 'bg-slate-800 text-white'
    }[type] || 'bg-slate-800 text-white';

    toast.className = `${colorClasses} pointer-events-auto px-4 py-3 rounded-xl shadow-lg flex items-center space-x-3 text-xs font-medium animate-fade-in transition-all duration-300`;
    toast.innerHTML = `
        <div class="flex-1">
            <span class="font-bold">${escapeHtml(title)}:</span> ${escapeHtml(message)}
        </div>
        <button onclick="this.parentElement.remove()" class="text-white/80 hover:text-white ml-2">
            <i class="fa-solid fa-xmark"></i>
        </button>
    `;

    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

/**
 * Currency Formatter
 */
function formatCurrency(amount) {
    const config = CURRENCY_CONFIG[userCurrency] || CURRENCY_CONFIG.INR;
    return new Intl.NumberFormat(config.locale, {
        style: 'currency',
        currency: config.currency,
        maximumFractionDigits: 2
    }).format(amount || 0);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
