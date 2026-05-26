#include "vulkan_renderer.h"
#include <cstring>
#include <array>
#include <jni.h>
#include <android/native_window_jni.h>

extern std::vector<uint32_t> getVertexShaderSpirV();
extern std::vector<uint32_t> getFragmentShaderSpirV();

struct Vertex {
    float pos[2];
    float uv[2];
};

static const Vertex QUAD_VERTICES[] = {
    {{-1.0f, -1.0f}, {0.0f, 1.0f}},
    {{ 1.0f, -1.0f}, {1.0f, 1.0f}},
    {{ 1.0f,  1.0f}, {1.0f, 0.0f}},
    {{-1.0f, -1.0f}, {0.0f, 1.0f}},
    {{ 1.0f,  1.0f}, {1.0f, 0.0f}},
    {{-1.0f,  1.0f}, {0.0f, 0.0f}},
};

VulkanRenderer::VulkanRenderer() = default;
VulkanRenderer::~VulkanRenderer() { destroy(); }

bool VulkanRenderer::init(ANativeWindow* window, uint32_t width, uint32_t height) {
    ctx.screenSize = {width, height};
    if (!createInstance()) return false;
    if (!createSurface(window)) return false;
    if (!pickPhysicalDevice()) return false;
    if (!createLogicalDevice()) return false;
    if (!createSwapchain(width, height)) return false;
    if (!createImageViews()) return false;
    if (!createRenderPass()) return false;
    if (!createDescriptorSetLayout()) return false;
    if (!createGraphicsPipeline()) return false;
    if (!createFramebuffers()) return false;
    if (!createCommandPool()) return false;
    if (!createVertexBuffer()) return false;
    if (!createTextureSampler()) return false;
    if (!createDescriptorPool()) return false;
    if (!createSyncObjects()) return false;
    ctx.initialized = true;
    LOGI("Vulkan initialized successfully (%ux%u)", width, height);
    return true;
}

bool VulkanRenderer::createInstance() {
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "KLVWWallpaper";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "KLVW";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = VK_API_VERSION_1_1;

    const char* extensions[] = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME
    };

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = 2;
    createInfo.ppEnabledExtensionNames = extensions;

    return vkCreateInstance(&createInfo, nullptr, &ctx.instance) == VK_SUCCESS;
}

bool VulkanRenderer::createSurface(ANativeWindow* window) {
    VkAndroidSurfaceCreateInfoKHR createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    createInfo.window = window;
    return vkCreateAndroidSurfaceKHR(ctx.instance, &createInfo, nullptr, &ctx.surface) == VK_SUCCESS;
}

bool VulkanRenderer::pickPhysicalDevice() {
    uint32_t count = 0;
    vkEnumeratePhysicalDevices(ctx.instance, &count, nullptr);
    if (count == 0) return false;
    std::vector<VkPhysicalDevice> devices(count);
    vkEnumeratePhysicalDevices(ctx.instance, &count, devices.data());

    for (auto& dev : devices) {
        uint32_t qCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(dev, &qCount, nullptr);
        std::vector<VkQueueFamilyProperties> props(qCount);
        vkGetPhysicalDeviceQueueFamilyProperties(dev, &qCount, props.data());
        for (uint32_t i = 0; i < qCount; i++) {
            if (props[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
                VkBool32 present = false;
                vkGetPhysicalDeviceSurfaceSupportKHR(dev, i, ctx.surface, &present);
                if (present) {
                    ctx.physicalDevice = dev;
                    ctx.graphicsQueueIndex = i;
                    return true;
                }
            }
        }
    }
    return false;
}

bool VulkanRenderer::createLogicalDevice() {
    float priority = 1.0f;
    VkDeviceQueueCreateInfo queueInfo{};
    queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueInfo.queueFamilyIndex = ctx.graphicsQueueIndex;
    queueInfo.queueCount = 1;
    queueInfo.pQueuePriorities = &priority;

    const char* extensions[] = { VK_KHR_SWAPCHAIN_EXTENSION_NAME };
    VkPhysicalDeviceFeatures features{};

    VkDeviceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    createInfo.queueCreateInfoCount = 1;
    createInfo.pQueueCreateInfos = &queueInfo;
    createInfo.enabledExtensionCount = 1;
    createInfo.ppEnabledExtensionNames = extensions;
    createInfo.pEnabledFeatures = &features;

    if (vkCreateDevice(ctx.physicalDevice, &createInfo, nullptr, &ctx.device) != VK_SUCCESS) return false;
    vkGetDeviceQueue(ctx.device, ctx.graphicsQueueIndex, 0, &ctx.graphicsQueue);
    return true;
}

bool VulkanRenderer::createSwapchain(uint32_t width, uint32_t height) {
    VkSurfaceCapabilitiesKHR caps;
    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(ctx.physicalDevice, ctx.surface, &caps);

    uint32_t fmtCount = 0;
    vkGetPhysicalDeviceSurfaceFormatsKHR(ctx.physicalDevice, ctx.surface, &fmtCount, nullptr);
    std::vector<VkSurfaceFormatKHR> formats(fmtCount);
    vkGetPhysicalDeviceSurfaceFormatsKHR(ctx.physicalDevice, ctx.surface, &fmtCount, formats.data());

    VkSurfaceFormatKHR chosen = formats[0];
    for (auto& f : formats) {
        if (f.format == VK_FORMAT_R8G8B8A8_UNORM && f.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
            chosen = f; break;
        }
    }
    ctx.swapchainFormat = chosen.format;
    ctx.swapchainExtent = {width, height};

    uint32_t imgCount = caps.minImageCount + 1;
    if (caps.maxImageCount > 0 && imgCount > caps.maxImageCount) imgCount = caps.maxImageCount;

    VkSwapchainCreateInfoKHR createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    createInfo.surface = ctx.surface;
    createInfo.minImageCount = imgCount;
    createInfo.imageFormat = chosen.format;
    createInfo.imageColorSpace = chosen.colorSpace;
    createInfo.imageExtent = ctx.swapchainExtent;
    createInfo.imageArrayLayers = 1;
    createInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    createInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    createInfo.preTransform = caps.currentTransform;
    createInfo.compositeAlpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
    createInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;
    createInfo.clipped = VK_TRUE;

    if (vkCreateSwapchainKHR(ctx.device, &createInfo, nullptr, &ctx.swapchain) != VK_SUCCESS) return false;

    uint32_t count = 0;
    vkGetSwapchainImagesKHR(ctx.device, ctx.swapchain, &count, nullptr);
    ctx.swapchainImages.resize(count);
    vkGetSwapchainImagesKHR(ctx.device, ctx.swapchain, &count, ctx.swapchainImages.data());
    return true;
}

bool VulkanRenderer::createImageViews() {
    ctx.swapchainImageViews.resize(ctx.swapchainImages.size());
    for (size_t i = 0; i < ctx.swapchainImages.size(); i++) {
        VkImageViewCreateInfo ci{};
        ci.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        ci.image = ctx.swapchainImages[i];
        ci.viewType = VK_IMAGE_VIEW_TYPE_2D;
        ci.format = ctx.swapchainFormat;
        ci.components = {VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY,
                         VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY};
        ci.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        if (vkCreateImageView(ctx.device, &ci, nullptr, &ctx.swapchainImageViews[i]) != VK_SUCCESS) return false;
    }
    return true;
}

bool VulkanRenderer::createRenderPass() {
    VkAttachmentDescription colorAttachment{};
    colorAttachment.format = ctx.swapchainFormat;
    colorAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
    colorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    colorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    colorAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    colorAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    colorAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    colorAttachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

    VkAttachmentReference ref{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &ref;

    VkRenderPassCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    ci.attachmentCount = 1;
    ci.pAttachments = &colorAttachment;
    ci.subpassCount = 1;
    ci.pSubpasses = &subpass;

    return vkCreateRenderPass(ctx.device, &ci, nullptr, &ctx.renderPass) == VK_SUCCESS;
}

bool VulkanRenderer::createDescriptorSetLayout() {
    VkDescriptorSetLayoutBinding binding{};
    binding.binding = 0;
    binding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    binding.descriptorCount = 1;
    binding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;

    VkDescriptorSetLayoutCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    ci.bindingCount = 1;
    ci.pBindings = &binding;

    return vkCreateDescriptorSetLayout(ctx.device, &ci, nullptr, &ctx.descriptorSetLayout) == VK_SUCCESS;
}

bool VulkanRenderer::createGraphicsPipeline() {
    auto vertCode = getVertexShaderSpirV();
    auto fragCode = getFragmentShaderSpirV();

    VkShaderModule vertMod = createShaderModule(vertCode);
    VkShaderModule fragMod = createShaderModule(fragCode);

    VkPipelineShaderStageCreateInfo stages[2] = {};
    stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    stages[0].module = vertMod;
    stages[0].pName = "main";
    stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    stages[1].module = fragMod;
    stages[1].pName = "main";

    VkVertexInputBindingDescription bindDesc{0, sizeof(Vertex), VK_VERTEX_INPUT_RATE_VERTEX};
    VkVertexInputAttributeDescription attrs[2] = {
        {0, 0, VK_FORMAT_R32G32_SFLOAT, offsetof(Vertex, pos)},
        {1, 0, VK_FORMAT_R32G32_SFLOAT, offsetof(Vertex, uv)},
    };

    VkPipelineVertexInputStateCreateInfo vi{};
    vi.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    vi.vertexBindingDescriptionCount = 1;
    vi.pVertexBindingDescriptions = &bindDesc;
    vi.vertexAttributeDescriptionCount = 2;
    vi.pVertexAttributeDescriptions = attrs;

    VkPipelineInputAssemblyStateCreateInfo ia{};
    ia.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    ia.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;

    VkViewport viewport{0.f, 0.f, (float)ctx.swapchainExtent.width, (float)ctx.swapchainExtent.height, 0.f, 1.f};
    VkRect2D scissor{{0,0}, ctx.swapchainExtent};

    VkPipelineViewportStateCreateInfo vs{};
    vs.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
    vs.viewportCount = 1;
    vs.pViewports = &viewport;
    vs.scissorCount = 1;
    vs.pScissors = &scissor;

    VkPipelineRasterizationStateCreateInfo rs{};
    rs.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    rs.polygonMode = VK_POLYGON_MODE_FILL;
    rs.cullMode = VK_CULL_MODE_NONE;
    rs.frontFace = VK_FRONT_FACE_CLOCKWISE;
    rs.lineWidth = 1.0f;

    VkPipelineMultisampleStateCreateInfo ms{};
    ms.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
    ms.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

    VkPipelineColorBlendAttachmentState cba{};
    cba.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                         VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    cba.blendEnable = VK_FALSE;

    VkPipelineColorBlendStateCreateInfo cb{};
    cb.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
    cb.attachmentCount = 1;
    cb.pAttachments = &cba;

    VkPipelineLayoutCreateInfo pl{};
    pl.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    pl.setLayoutCount = 1;
    pl.pSetLayouts = &ctx.descriptorSetLayout;

    if (vkCreatePipelineLayout(ctx.device, &pl, nullptr, &ctx.pipelineLayout) != VK_SUCCESS) return false;

    VkGraphicsPipelineCreateInfo pci{};
    pci.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pci.stageCount = 2;
    pci.pStages = stages;
    pci.pVertexInputState = &vi;
    pci.pInputAssemblyState = &ia;
    pci.pViewportState = &vs;
    pci.pRasterizationState = &rs;
    pci.pMultisampleState = &ms;
    pci.pColorBlendState = &cb;
    pci.layout = ctx.pipelineLayout;
    pci.renderPass = ctx.renderPass;
    pci.subpass = 0;

    bool ok = vkCreateGraphicsPipelines(ctx.device, VK_NULL_HANDLE, 1, &pci, nullptr, &ctx.graphicsPipeline) == VK_SUCCESS;
    vkDestroyShaderModule(ctx.device, vertMod, nullptr);
    vkDestroyShaderModule(ctx.device, fragMod, nullptr);
    return ok;
}

bool VulkanRenderer::createFramebuffers() {
    ctx.framebuffers.resize(ctx.swapchainImageViews.size());
    for (size_t i = 0; i < ctx.swapchainImageViews.size(); i++) {
        VkFramebufferCreateInfo ci{};
        ci.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        ci.renderPass = ctx.renderPass;
        ci.attachmentCount = 1;
        ci.pAttachments = &ctx.swapchainImageViews[i];
        ci.width = ctx.swapchainExtent.width;
        ci.height = ctx.swapchainExtent.height;
        ci.layers = 1;
        if (vkCreateFramebuffer(ctx.device, &ci, nullptr, &ctx.framebuffers[i]) != VK_SUCCESS) return false;
    }
    return true;
}

bool VulkanRenderer::createCommandPool() {
    VkCommandPoolCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    ci.queueFamilyIndex = ctx.graphicsQueueIndex;
    ci.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    return vkCreateCommandPool(ctx.device, &ci, nullptr, &ctx.commandPool) == VK_SUCCESS;
}

bool VulkanRenderer::createVertexBuffer() {
    VkDeviceSize size = sizeof(QUAD_VERTICES);
    if (!createBuffer(size,
        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        ctx.vertexBuffer, ctx.vertexBufferMemory)) return false;

    void* data;
    vkMapMemory(ctx.device, ctx.vertexBufferMemory, 0, size, 0, &data);
    memcpy(data, QUAD_VERTICES, size);
    vkUnmapMemory(ctx.device, ctx.vertexBufferMemory);
    return true;
}

bool VulkanRenderer::createTextureSampler() {
    VkSamplerCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    ci.magFilter = VK_FILTER_LINEAR;
    ci.minFilter = VK_FILTER_LINEAR;
    ci.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    ci.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    ci.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    ci.anisotropyEnable = VK_FALSE;
    ci.borderColor = VK_BORDER_COLOR_INT_OPAQUE_BLACK;
    ci.unnormalizedCoordinates = VK_FALSE;
    ci.compareEnable = VK_FALSE;
    ci.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;
    return vkCreateSampler(ctx.device, &ci, nullptr, &ctx.textureSampler) == VK_SUCCESS;
}

bool VulkanRenderer::createDescriptorPool() {
    VkDescriptorPoolSize poolSize{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1};
    VkDescriptorPoolCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    ci.poolSizeCount = 1;
    ci.pPoolSizes = &poolSize;
    ci.maxSets = 1;
    return vkCreateDescriptorPool(ctx.device, &ci, nullptr, &ctx.descriptorPool) == VK_SUCCESS;
}

bool VulkanRenderer::createDescriptorSets() {
    VkDescriptorSetAllocateInfo ai{};
    ai.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    ai.descriptorPool = ctx.descriptorPool;
    ai.descriptorSetCount = 1;
    ai.pSetLayouts = &ctx.descriptorSetLayout;
    if (vkAllocateDescriptorSets(ctx.device, &ai, &ctx.descriptorSet) != VK_SUCCESS) return false;

    VkDescriptorImageInfo imgInfo{ctx.textureSampler, ctx.textureImageView, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkWriteDescriptorSet write{};
    write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    write.dstSet = ctx.descriptorSet;
    write.dstBinding = 0;
    write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    write.descriptorCount = 1;
    write.pImageInfo = &imgInfo;
    vkUpdateDescriptorSets(ctx.device, 1, &write, 0, nullptr);
    return true;
}

bool VulkanRenderer::createCommandBuffers() {
    ctx.commandBuffers.resize(ctx.framebuffers.size());
    VkCommandBufferAllocateInfo ai{};
    ai.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    ai.commandPool = ctx.commandPool;
    ai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    ai.commandBufferCount = (uint32_t)ctx.commandBuffers.size();
    return vkAllocateCommandBuffers(ctx.device, &ai, ctx.commandBuffers.data()) == VK_SUCCESS;
}

bool VulkanRenderer::createSyncObjects() {
    VkSemaphoreCreateInfo si{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    VkFenceCreateInfo fi{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    fi.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    return vkCreateSemaphore(ctx.device, &si, nullptr, &ctx.imageAvailableSemaphore) == VK_SUCCESS &&
           vkCreateSemaphore(ctx.device, &si, nullptr, &ctx.renderFinishedSemaphore) == VK_SUCCESS &&
           vkCreateFence(ctx.device, &fi, nullptr, &ctx.inFlightFence) == VK_SUCCESS;
}

bool VulkanRenderer::setImage(const uint8_t* pixels, uint32_t imgWidth, uint32_t imgHeight) {
    if (!ctx.initialized) return false;
    vkDeviceWaitIdle(ctx.device);

    if (ctx.textureImage != VK_NULL_HANDLE) {
        vkDestroyImageView(ctx.device, ctx.textureImageView, nullptr);
        vkDestroyImage(ctx.device, ctx.textureImage, nullptr);
        vkFreeMemory(ctx.device, ctx.textureMemory, nullptr);
        vkResetDescriptorPool(ctx.device, ctx.descriptorPool, 0);
    }

    if (!uploadTexture(pixels, imgWidth, imgHeight)) return false;

    VkImageViewCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    ci.image = ctx.textureImage;
    ci.viewType = VK_IMAGE_VIEW_TYPE_2D;
    ci.format = VK_FORMAT_R8G8B8A8_UNORM;
    ci.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    if (vkCreateImageView(ctx.device, &ci, nullptr, &ctx.textureImageView) != VK_SUCCESS) return false;

    if (!createDescriptorSets()) return false;
    if (!createCommandBuffers()) return false;

    for (size_t i = 0; i < ctx.commandBuffers.size(); i++) {
        VkCommandBufferBeginInfo bi{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        vkBeginCommandBuffer(ctx.commandBuffers[i], &bi);

        VkClearValue clear{{0.f, 0.f, 0.f, 1.f}};
        VkRenderPassBeginInfo rpi{};
        rpi.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        rpi.renderPass = ctx.renderPass;
        rpi.framebuffer = ctx.framebuffers[i];
        rpi.renderArea = {{0,0}, ctx.swapchainExtent};
        rpi.clearValueCount = 1;
        rpi.pClearValues = &clear;

        vkCmdBeginRenderPass(ctx.commandBuffers[i], &rpi, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdBindPipeline(ctx.commandBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, ctx.graphicsPipeline);
        VkDeviceSize offset = 0;
        vkCmdBindVertexBuffers(ctx.commandBuffers[i], 0, 1, &ctx.vertexBuffer, &offset);
        vkCmdBindDescriptorSets(ctx.commandBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS,
            ctx.pipelineLayout, 0, 1, &ctx.descriptorSet, 0, nullptr);
        vkCmdDraw(ctx.commandBuffers[i], 6, 1, 0, 0);
        vkCmdEndRenderPass(ctx.commandBuffers[i]);
        vkEndCommandBuffer(ctx.commandBuffers[i]);
    }
    return true;
}

bool VulkanRenderer::render() {
    if (!ctx.initialized || ctx.textureImage == VK_NULL_HANDLE) return false;

    vkWaitForFences(ctx.device, 1, &ctx.inFlightFence, VK_TRUE, UINT64_MAX);
    vkResetFences(ctx.device, 1, &ctx.inFlightFence);

    uint32_t imageIndex = 0;
    VkResult result = vkAcquireNextImageKHR(ctx.device, ctx.swapchain, UINT64_MAX,
        ctx.imageAvailableSemaphore, VK_NULL_HANDLE, &imageIndex);
    if (result != VK_SUCCESS) return false;

    VkSemaphore waitSems[] = {ctx.imageAvailableSemaphore};
    VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
    VkSemaphore signalSems[] = {ctx.renderFinishedSemaphore};

    VkSubmitInfo si{};
    si.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    si.waitSemaphoreCount = 1;
    si.pWaitSemaphores = waitSems;
    si.pWaitDstStageMask = waitStages;
    si.commandBufferCount = 1;
    si.pCommandBuffers = &ctx.commandBuffers[imageIndex];
    si.signalSemaphoreCount = 1;
    si.pSignalSemaphores = signalSems;

    if (vkQueueSubmit(ctx.graphicsQueue, 1, &si, ctx.inFlightFence) != VK_SUCCESS) return false;

    VkPresentInfoKHR pi{};
    pi.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    pi.waitSemaphoreCount = 1;
    pi.pWaitSemaphores = signalSems;
    pi.swapchainCount = 1;
    pi.pSwapchains = &ctx.swapchain;
    pi.pImageIndices = &imageIndex;
    vkQueuePresentKHR(ctx.graphicsQueue, &pi);
    return true;
}

bool VulkanRenderer::uploadTexture(const uint8_t* pixels, uint32_t w, uint32_t h) {
    VkDeviceSize size = (VkDeviceSize)w * h * 4;

    VkBuffer stagingBuf;
    VkDeviceMemory stagingMem;
    if (!createBuffer(size,
        VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        stagingBuf, stagingMem)) return false;

    void* data;
    vkMapMemory(ctx.device, stagingMem, 0, size, 0, &data);
    memcpy(data, pixels, size);
    vkUnmapMemory(ctx.device, stagingMem);

    if (!createImageVk(w, h, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_TILING_OPTIMAL,
        VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, ctx.textureImage, ctx.textureMemory)) return false;

    transitionImageLayout(ctx.textureImage, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    copyBufferToImage(stagingBuf, ctx.textureImage, w, h);
    transitionImageLayout(ctx.textureImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

    vkDestroyBuffer(ctx.device, stagingBuf, nullptr);
    vkFreeMemory(ctx.device, stagingMem, nullptr);
    return true;
}

bool VulkanRenderer::createBuffer(VkDeviceSize size, VkBufferUsageFlags usage,
                                   VkMemoryPropertyFlags props, VkBuffer& buf, VkDeviceMemory& mem) {
    VkBufferCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    ci.size = size;
    ci.usage = usage;
    ci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateBuffer(ctx.device, &ci, nullptr, &buf) != VK_SUCCESS) return false;

    VkMemoryRequirements req;
    vkGetBufferMemoryRequirements(ctx.device, buf, &req);

    VkMemoryAllocateInfo ai{};
    ai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    ai.allocationSize = req.size;
    ai.memoryTypeIndex = findMemoryType(req.memoryTypeBits, props);
    if (vkAllocateMemory(ctx.device, &ai, nullptr, &mem) != VK_SUCCESS) return false;
    vkBindBufferMemory(ctx.device, buf, mem, 0);
    return true;
}

bool VulkanRenderer::createImageVk(uint32_t w, uint32_t h, VkFormat format, VkImageTiling tiling,
                                    VkImageUsageFlags usage, VkMemoryPropertyFlags props,
                                    VkImage& image, VkDeviceMemory& memory) {
    VkImageCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    ci.imageType = VK_IMAGE_TYPE_2D;
    ci.extent = {w, h, 1};
    ci.mipLevels = 1;
    ci.arrayLayers = 1;
    ci.format = format;
    ci.tiling = tiling;
    ci.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    ci.usage = usage;
    ci.samples = VK_SAMPLE_COUNT_1_BIT;
    ci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateImage(ctx.device, &ci, nullptr, &image) != VK_SUCCESS) return false;

    VkMemoryRequirements req;
    vkGetImageMemoryRequirements(ctx.device, image, &req);
    VkMemoryAllocateInfo ai{};
    ai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    ai.allocationSize = req.size;
    ai.memoryTypeIndex = findMemoryType(req.memoryTypeBits, props);
    if (vkAllocateMemory(ctx.device, &ai, nullptr, &memory) != VK_SUCCESS) return false;
    vkBindImageMemory(ctx.device, image, memory, 0);
    return true;
}

VkCommandBuffer VulkanRenderer::beginSingleTimeCommands() {
    VkCommandBufferAllocateInfo ai{};
    ai.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    ai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    ai.commandPool = ctx.commandPool;
    ai.commandBufferCount = 1;
    VkCommandBuffer cmd;
    vkAllocateCommandBuffers(ctx.device, &ai, &cmd);
    VkCommandBufferBeginInfo bi{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmd, &bi);
    return cmd;
}

void VulkanRenderer::endSingleTimeCommands(VkCommandBuffer cmd) {
    vkEndCommandBuffer(cmd);
    VkSubmitInfo si{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    si.commandBufferCount = 1;
    si.pCommandBuffers = &cmd;
    vkQueueSubmit(ctx.graphicsQueue, 1, &si, VK_NULL_HANDLE);
    vkQueueWaitIdle(ctx.graphicsQueue);
    vkFreeCommandBuffers(ctx.device, ctx.commandPool, 1, &cmd);
}

void VulkanRenderer::transitionImageLayout(VkImage image, VkImageLayout oldLayout, VkImageLayout newLayout) {
    VkCommandBuffer cmd = beginSingleTimeCommands();
    VkImageMemoryBarrier barrier{};
    barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.oldLayout = oldLayout;
    barrier.newLayout = newLayout;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = image;
    barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};

    VkPipelineStageFlags src = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
    VkPipelineStageFlags dst = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;

    if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
        barrier.srcAccessMask = 0;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        dst = VK_PIPELINE_STAGE_TRANSFER_BIT;
    } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        src = VK_PIPELINE_STAGE_TRANSFER_BIT;
    }
    vkCmdPipelineBarrier(cmd, src, dst, 0, 0, nullptr, 0, nullptr, 1, &barrier);
    endSingleTimeCommands(cmd);
}

void VulkanRenderer::copyBufferToImage(VkBuffer buffer, VkImage image, uint32_t w, uint32_t h) {
    VkCommandBuffer cmd = beginSingleTimeCommands();
    VkBufferImageCopy region{};
    region.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    region.imageExtent = {w, h, 1};
    vkCmdCopyBufferToImage(cmd, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);
    endSingleTimeCommands(cmd);
}

uint32_t VulkanRenderer::findMemoryType(uint32_t typeFilter, VkMemoryPropertyFlags props) {
    VkPhysicalDeviceMemoryProperties memProps;
    vkGetPhysicalDeviceMemoryProperties(ctx.physicalDevice, &memProps);
    for (uint32_t i = 0; i < memProps.memoryTypeCount; i++) {
        if ((typeFilter & (1 << i)) && (memProps.memoryTypes[i].propertyFlags & props) == props) return i;
    }
    return 0;
}

VkShaderModule VulkanRenderer::createShaderModule(const std::vector<uint32_t>& code) {
    VkShaderModuleCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    ci.codeSize = code.size() * sizeof(uint32_t);
    ci.pCode = code.data();
    VkShaderModule mod;
    vkCreateShaderModule(ctx.device, &ci, nullptr, &mod);
    return mod;
}

void VulkanRenderer::cleanupSwapchain() {
    for (auto fb : ctx.framebuffers) vkDestroyFramebuffer(ctx.device, fb, nullptr);
    for (auto iv : ctx.swapchainImageViews) vkDestroyImageView(ctx.device, iv, nullptr);
    if (ctx.swapchain) vkDestroySwapchainKHR(ctx.device, ctx.swapchain, nullptr);
}

void VulkanRenderer::destroy() {
    if (!ctx.device) return;
    vkDeviceWaitIdle(ctx.device);
    cleanupSwapchain();
    if (ctx.textureSampler) vkDestroySampler(ctx.device, ctx.textureSampler, nullptr);
    if (ctx.textureImageView) vkDestroyImageView(ctx.device, ctx.textureImageView, nullptr);
    if (ctx.textureImage) vkDestroyImage(ctx.device, ctx.textureImage, nullptr);
    if (ctx.textureMemory) vkFreeMemory(ctx.device, ctx.textureMemory, nullptr);
    if (ctx.vertexBuffer) vkDestroyBuffer(ctx.device, ctx.vertexBuffer, nullptr);
    if (ctx.vertexBufferMemory) vkFreeMemory(ctx.device, ctx.vertexBufferMemory, nullptr);
    if (ctx.descriptorPool) vkDestroyDescriptorPool(ctx.device, ctx.descriptorPool, nullptr);
    if (ctx.descriptorSetLayout) vkDestroyDescriptorSetLayout(ctx.device, ctx.descriptorSetLayout, nullptr);
    if (ctx.graphicsPipeline) vkDestroyPipeline(ctx.device, ctx.graphicsPipeline, nullptr);
    if (ctx.pipelineLayout) vkDestroyPipelineLayout(ctx.device, ctx.pipelineLayout, nullptr);
    if (ctx.renderPass) vkDestroyRenderPass(ctx.device, ctx.renderPass, nullptr);
    if (ctx.commandPool) vkDestroyCommandPool(ctx.device, ctx.commandPool, nullptr);
    if (ctx.imageAvailableSemaphore) vkDestroySemaphore(ctx.device, ctx.imageAvailableSemaphore, nullptr);
    if (ctx.renderFinishedSemaphore) vkDestroySemaphore(ctx.device, ctx.renderFinishedSemaphore, nullptr);
    if (ctx.inFlightFence) vkDestroyFence(ctx.device, ctx.inFlightFence, nullptr);
    if (ctx.surface) vkDestroySurfaceKHR(ctx.instance, ctx.surface, nullptr);
    if (ctx.device) vkDestroyDevice(ctx.device, nullptr);
    if (ctx.instance) vkDestroyInstance(ctx.instance, nullptr);
    ctx = {};
}

// JNI Bridge
static VulkanRenderer* gRenderer = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_klvw_wallpaper_service_renderer_VulkanBridge_nativeInit(
    JNIEnv* env, jobject, jobject surface, jint width, jint height) {
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) return JNI_FALSE;
    delete gRenderer;
    gRenderer = new VulkanRenderer();
    bool ok = gRenderer->init(window, (uint32_t)width, (uint32_t)height);
    ANativeWindow_release(window);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_klvw_wallpaper_service_renderer_VulkanBridge_nativeSetImage(
    JNIEnv* env, jobject, jbyteArray pixels, jint width, jint height) {
    if (!gRenderer) return JNI_FALSE;
    jsize len = env->GetArrayLength(pixels);
    jbyte* data = env->GetByteArrayElements(pixels, nullptr);
    bool ok = gRenderer->setImage(reinterpret_cast<const uint8_t*>(data), (uint32_t)width, (uint32_t)height);
    env->ReleaseByteArrayElements(pixels, data, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_klvw_wallpaper_service_renderer_VulkanBridge_nativeRender(
    JNIEnv*, jobject) {
    if (!gRenderer) return JNI_FALSE;
    return gRenderer->render() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_klvw_wallpaper_service_renderer_VulkanBridge_nativeDestroy(
    JNIEnv*, jobject) {
    delete gRenderer;
    gRenderer = nullptr;
}

} // extern "C"
