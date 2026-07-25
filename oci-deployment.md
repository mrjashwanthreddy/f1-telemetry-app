# Complete Oracle Cloud Deployment Guide (Step-by-Step)

This master guide covers **every single click, field, setting, and command** to deploy the F1 Telemetry backend on Oracle Cloud **100% Always Free**.

---

## STEP 1: Create Compute Instance (On-Screen Settings)

Go to Oracle Cloud Console: ☰ Menu → **Compute** → **Instances** → Click **Create instance**.

### 1. Basic Information
- **Name**: Enter `f1-telemetry-backend`
- **Create in compartment**: Leave as default root compartment.

### 2. Placement
- **Availability Domain**: Leave default `AD-1` (or select `AD-2` / `AD-3` if ARM capacity is full).

### 3. Image and Shape
Click **Edit** on the right side of the Image and Shape box:
- **Image**:
  1. Click **Change image**.
  2. Select **Oracle Linux 9** (or **Canonical Ubuntu 22.04**).
  3. Ensure the architecture selected is **aarch64 / ARM**.
  4. Click **Select image**.
- **Shape**:
  1. Click **Change shape**.
  2. Select **Ampere (Arm-based processor)**.
  3. Check **`VM.Standard.A1.Flex`** *(Tagged Always Free-eligible)*.
  4. Drag slider: **OCPUs = 2**, **Memory = 12 GB**.
  5. Click **Select shape**.

### 4. Networking (CRITICAL)
Under the **Networking** section, select:
- **Primary network**: Select radio button **Create new virtual cloud network**.
- **New virtual cloud network name**: `f1-vcn` (or leave default).
- **Subnet**: Select radio button **Create new public subnet**.
- **New public subnet name**: `f1-public-subnet` (or leave default).
- **Assign a public IPv4 address**: Select radio button **Automatically assign public IPv4 address** (Yes).

### 5. Add SSH Keys (Save Credentials)
- Select radio button **Generate a key pair for me**.
- Click **Save private key** button.
  > 📁 A file named `ssh-key-2026-07-24.key` (or similar) will download to your `Downloads` folder on your PC. **DO NOT DELETE THIS FILE.**

### 6. Boot Volume
- Leave as default (46.6 GB) — 100% Always Free.

### 7. Click CREATE
- Click the blue **Create** button at the bottom left.
- Status will show **PROVISIONING** (yellow icon). Wait 1 to 2 minutes until it changes to **RUNNING** (green icon).

---

## STEP 2: Where to Find Your Instance Info (Public IP)

Once status is **RUNNING**, look at the **Instance Details** page:

1. Locate **Public IP Address** (e.g. `140.238.45.120`). **Copy this IP!**
2. Locate **Username**:
   - If using **Oracle Linux**: Username is `opc`
   - If using **Ubuntu**: Username is `ubuntu`

---

## STEP 3: Open Firewall Port 8080 (Security List Ingress Rule)

Allow traffic to reach port 8080 from your desktop `.exe`:

1. Click top-left ☰ Menu → **Networking** → **Virtual cloud networks**.
2. Click your VCN name (`f1-vcn`).
3. In left sidebar under **Resources**, click **Subnets**.
4. Click your public subnet (`f1-public-subnet`).
5. In left sidebar under **Resources**, click **Security Lists**.
6. Click **Default Security List for f1-vcn**.
7. Click blue **Add Ingress Rules** button:
   - **Source Type**: `CIDR`
   - **Source CIDR**: `0.0.0.0/0`
   - **IP Protocol**: `TCP`
   - **Destination Port Range**: `8080`
   - **Description**: `Allow F1 Telemetry backend port`
8. Click **Add Ingress Rules**.

---

## STEP 4: Connect to Server via SSH (PowerShell)

On your local Windows PC:

1. Open **PowerShell**.
2. Navigate to your Downloads folder:
   ```powershell
   cd ~\Downloads
   ```
3. Run the SSH command (replace `<YOUR-PUBLIC-IP>` with your actual Public IP from Step 2):
   ```powershell
# Connect to your Oracle VM
ssh -i .\ssh-key-2026-07-24.key opc@140.245.202.15
```
   *(If prompted `Are you sure you want to continue connecting (yes/no)?`, type `yes` and hit Enter).*

---

## STEP 5: Install Docker & Deploy Backend (On Server)

Once connected via SSH to the server, paste these exact commands line by line:

```bash
# 1. Install Docker & Git
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
newgrp docker

# 2. Open internal Linux OS firewall for port 8080
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload

# 3. Clone Repository
git clone https://github.com/mrjashwanthreddy/f1-telemetry-app.git
cd f1-telemetry-app

# 4. Build Docker Container
docker build -t f1-telemetry-backend .

# 5. Launch Backend Server Centrally
docker run -d \
  --name f1-backend \
  --restart always \
  -p 8080:8080 \
  f1-telemetry-backend
```

Test that your backend is live by visiting `http://<YOUR-PUBLIC-IP>:8080` in your web browser!

---

## STEP 6: Update Local Client Config for `.exe` Packaging

In your local repository file `src/main/resources/application.properties`:

Set your Oracle server URL:
```properties
remote.backend.url=http://<YOUR-PUBLIC-IP>:8080
```

Now when you package your desktop `.exe` using `package-app.bat`, users running the `.exe` will connect directly to your Oracle Cloud backend!
